package arcanum.engine.sets.dmu

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The DMU sweep: siblings of the bugs DMU set work fixed, probed on the paths the fixes didn't touch. A divided amount capping the
 * targets of an activated ability; the life a player can't pay toward a spell or ability through alternative, additional and
 * optional costs and life-for-mana; the last known information of an object a trigger or "its controller" names after it left;
 * and "it" naming the object an earlier instruction moved or made rather than the source.
 */
class DmuSweepScenariosTest {
    private val bear = Cards.invent("Sweep Probe Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Sweep Probe Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")

    private fun Scenario.canCast(name: String): Boolean = Engine.options(state, alice.id).any { it is Input.Cast && it.card == card(name) }

    // ---- A divided amount on an activated ability (the cap was for spells and triggers)

    private val catapult = Cards.invent("Sweep Probe Catapult", "{2}", "Artifact",
        "{X}, {T}: Sweep Probe Catapult deals X damage divided as you choose among any number of targets.")

    // CR 601.2d, 602.2b: an activated ability's division too: X = 1 can go to one target at most
    @Test
    fun anActivatedDivisionAllowsNoMoreTargetsThanItHas() = scenario {
        alice.battlefield(catapult, "Mountain")
        bob.battlefield(bear, ogre)
        val ability = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().first { it.source == card(catapult) }
        perform(ability)
        while (pending is Decision.Announce) answer(Answer.Number(1))
        assertEquals(1, decision<Decision.Targets>().max)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Targets(listOf(target(bear), target(ogre)))) }
    }

    // ---- Life a player can't pay to cast spells or activate abilities: the other ways life pays a cost

    private val vow = Cards.invent("Sweep Probe Bloodless Vow", "{3}", "Artifact",
        "Players can't pay life to cast spells or to activate abilities that aren't mana abilities.")
    private val gasp = Cards.invent("Sweep Probe Final Gasp", "{3}{B}", "Instant",
        "You may pay 4 life rather than pay this spell's mana cost.\nDestroy target creature.")
    private val pact = Cards.invent("Sweep Probe Blood Pact", "{1}{B}", "Sorcery",
        "As an additional cost to cast this spell, pay 3 life.\nDraw two cards.")
    private val deluge = Cards.invent("Sweep Probe Black Deluge", "{2}{B}", "Sorcery",
        "As an additional cost to cast this spell, pay X life.\nAll creatures get -X/-X until end of turn.")
    private val despoiler = Cards.invent("Sweep Probe Despoiler", "{3}{G}{G}", "Creature — Phyrexian Wurm", """
        As an additional cost to cast green permanent spells, you may pay 2 life. Those spells cost {G} less to cast if you paid life this way. This effect reduces only the amount of green mana you pay.
    """, "6/6")
    private val krrik = Cards.invent("Sweep Probe Bloodtithe Herald", "{4}{B}", "Creature — Phyrexian", """
        For each {B} in a cost, you may pay 2 life rather than pay that mana.
    """, "2/2")
    private val tithe = Cards.invent("Sweep Probe Tithe", "{B}", "Sorcery", "Draw a card.")

    // CR 118.9, 119.4, 601.2b: an alternative cost of life is life paid to cast a spell
    @Test
    fun theVowStopsAnAlternativeCostOfLife() = scenario(players = 3) {
        alice.hand(gasp)
        bob.battlefield(bear)
        assertTrue(canCast(gasp))
        carol.battlefield(vow)
        assertFalse(canCast(gasp))
    }

    // CR 601.2b, 601.2h, 119.4: an additional cost of life too
    @Test
    fun theVowStopsAnAdditionalCostOfLife() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(pact)
        alice.library("Island", "Island")
        assertTrue(canCast(pact))
        bob.battlefield(vow)
        assertFalse(canCast(pact))
    }

    // CR 119.4b: 0 life can always be paid, even when players can't pay life
    @Test
    fun zeroLifeIsPaidUnderTheVow() = scenario {
        alice.battlefield(vow, "Swamp", "Swamp", "Swamp", bear)
        alice.hand(deluge)
        alice.casts(deluge, x = 0)
        resolveAll()
        expect {
            alice.graveyard has deluge
            alice.battlefield has bear
            alice.life shouldBe 20
        }
    }

    // CR 601.2b, 601.2f, 119.4: the optional life of an additional cost can't be paid, so the {G} it would save stays: one Forest
    // can't cast a {1}{G} creature
    @Test
    fun theVowStopsAnOptionalAdditionalCostOfLife() = scenario {
        alice.battlefield(despoiler, "Forest")
        alice.hand(bear)
        assertTrue(canCast(bear))
        bob.battlefield(vow)
        assertFalse(canCast(bear))
    }

    // CR 601.2b, 119.4: with two Forests the creature is cast for its full cost and no life is offered or taken
    @Test
    fun underTheVowTheOptionalLifeIsNotTaken() = scenario {
        alice.battlefield(despoiler, vow, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        if (pending is Decision.May) assertFailsWith<IllegalArgumentException> { answer(Answer.YesNo(true)) }
        if (pending is Decision.May) answer(Answer.YesNo(false))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.life shouldBe 20
        }
    }

    // CR 119.4, 601.2h: life paid rather than {B} is life paid to cast the spell
    @Test
    fun theVowStopsLifeForBlackMana() = scenario(players = 3) {
        alice.battlefield(krrik)
        alice.hand(tithe)
        alice.library("Island")
        assertTrue(canCast(tithe))
        carol.battlefield(vow)
        assertFalse(canCast(tithe))
    }

    private val warded = Cards.invent("Sweep Probe Warded Ogre", "{2}{R}", "Creature — Ogre", "Ward—Pay 3 life.", "3/3")
    private val bolt = Cards.invent("Sweep Probe Bolt", "{R}", "Instant", "Sweep Probe Bolt deals 3 damage to target creature.")

    // CR 702.21a, 119.4: ward's life is paid as its triggered ability resolves, not to cast a spell, so the Vow doesn't stop it
    @Test
    fun wardLifeIsStillPaidUnderTheVow() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand(bolt)
        bob.battlefield(warded)
        carol.battlefield(vow)
        alice.casts(bolt, target = warded)
        resolve()
        when (val d = pending) {
            is Decision.May -> answer(Answer.YesNo(true))
            is Decision.ChooseOption -> answer(Answer.Option(d.options.first { it.contains("life", ignoreCase = true) || it.contains("Pay") }))
            else -> {}
        }
        resolveAll()
        expect {
            alice.life shouldBe 17
            bob.graveyard has warded
        }
    }

    // ---- Last known information of an object that has left

    private val mirrorling = Cards.invent("Sweep Probe Mirrorling", "{2}{U}", "Creature — Shapeshifter",
        "Whenever this creature becomes the target of a spell, create a token that's a copy of it.", "2/3")
    private val murder = Cards.invent("Sweep Probe Murder", "{1}{B}{B}", "Instant", "Destroy target creature.")
    private val wrath = Cards.invent("Sweep Probe Upheaval", "{2}{W}{W}", "Instant", "Destroy all creatures.")

    // CR 608.2h, 707.2: the source has died before its trigger resolves, so the token copies it as it last existed
    @Test
    fun aBecomesTargetTriggerCopiesItsSourceThatHasLeft() = scenario(players = 3) {
        alice.battlefield(mirrorling)
        bob.hand(murder)
        carol.hand(wrath)
        bob.castsWithoutCosts(murder, target = mirrorling)
        carol.castsWithoutCosts(wrath)
        resolve()
        expect { alice.graveyard has mirrorling }
        resolveAll()
        val tokens = view().objects.values.filter { it.zone == Zone.Battlefield && it.obj?.isToken == true }
        assertEquals(listOf("Sweep Probe Mirrorling"), tokens.map { it.characteristics?.name })
    }

    private val reaping = Cards.invent("Sweep Probe Reaping", "{1}{B}", "Instant",
        "Destroy target creature. Its controller creates a 2/2 black Zombie creature token.")

    // CR 608.2h: "its controller" of a creature destroyed is the player who controlled it as it last existed, not its owner
    @Test
    fun itsControllerOfAStolenCreatureThatDiedIsWhoControlledIt() = scenario(players = 3) {
        bob.battlefield(bear)
        effect(carol, Mechanic.ControlledBy(Players.You), on = listOf(bear))
        alice.hand(reaping)
        alice.castsWithoutCosts(reaping, target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
        val zombies = view().objects.values.filter { it.zone == Zone.Battlefield && it.obj?.isToken == true }
        assertEquals(listOf(carol.id), zombies.map { it.controller })
    }

    // ---- "It" names the object an earlier instruction moved or made, not the source (CR 608.2c)

    private val reviver = Cards.invent("Sweep Probe Reviver", "{3}{B}", "Creature — Zombie Cleric",
        "When this creature enters, return target creature card with mana value 2 or less from your graveyard to the battlefield. Put a +1/+1 counter on it.", "2/2")

    // CR 608.2c, 400.7: the counter goes on the creature returned
    @Test
    fun aCounterOnItGoesOnTheReturnedCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(reviver)
        alice.graveyard(bear)
        alice.casts(reviver)
        resolve()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect { alice.battlefield has bear }
        assertEquals(1, view()[card(bear)]?.obj?.counters(CounterKind.PowerToughness(1, 1)))
        assertEquals(0, view()[card(reviver)]?.obj?.counters(CounterKind.PowerToughness(1, 1)))
    }

    private val unearther = Cards.invent("Sweep Probe Unearther", "{2}{B}", "Creature — Human Warlock", """
        When this creature enters, mill three cards. You may put a creature card from among them onto the battlefield. Sacrifice it at the beginning of the next end step.
    """, "1/1")

    // CR 603.7c, 608.2c: the delayed trigger sacrifices the creature put onto the battlefield, not the source
    @Test
    fun theDelayedSacrificeTakesTheCreaturePutOntoTheBattlefield() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(unearther)
        alice.library(bear, "Island", "Island")
        alice.casts(unearther)
        resolveAll()
        while (pending != null && pending !is Decision.Targets) {
            when (val d = pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card(bear))))
                else -> break
            }
        }
        resolveAll()
        expect { alice.battlefield has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.battlefield has unearther
        }
    }

    private val seer = Cards.invent("Sweep Probe Seer", "{2}{G}", "Creature — Elf Druid", """
        When this creature enters, reveal the top card of your library. If it's a creature card, put it onto the battlefield. Otherwise, put it into your graveyard.
    """, "1/1")

    // CR 608.2c, 701.20a: "it" is the revealed card: a creature card enters, the source stays
    @Test
    fun aRevealedCreatureCardIsPutOntoTheBattlefield() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(seer)
        alice.library(bear)
        alice.casts(seer)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has seer
        }
    }

    // CR 608.2c: a revealed land goes to the graveyard, the source stays on the battlefield
    @Test
    fun aRevealedLandGoesToTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(seer)
        alice.library("Island")
        alice.casts(seer)
        resolveAll()
        expect {
            alice.graveyard has "Island"
            alice.battlefield has seer
        }
    }

    private val conjurer = Cards.invent("Sweep Probe Ember Conjurer", "{2}{R}", "Creature — Human Shaman", """
        When this creature enters, create a 3/1 red Elemental creature token with haste. Exile it at the beginning of the next end step.
    """, "2/2")

    // CR 603.7c, 608.2c: the delayed trigger exiles the token, not the source
    @Test
    fun theDelayedExileTakesTheToken() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(conjurer)
        alice.casts(conjurer)
        resolveAll()
        assertEquals(1, view().objects.values.count { it.zone == Zone.Battlefield && it.obj?.isToken == true })
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(0, view().objects.values.count { it.zone == Zone.Battlefield && it.obj?.isToken == true })
        expect { alice.battlefield has conjurer }
    }

    private val blinker = Cards.invent("Sweep Probe Blinker", "{2}{W}", "Creature — Human Wizard", """
        When this creature enters, exile another target creature you control, then return it to the battlefield under its owner's control. Put a +1/+1 counter on it.
    """, "2/2")

    // CR 400.7, 608.2c: "it" after the return is the new object on the battlefield, and the counter goes on it
    @Test
    fun aCounterOnTheBlinkedCreatureGoesOnTheReturnedOne() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(blinker)
        alice.casts(blinker)
        resolve()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect { alice.battlefield has bear }
        assertEquals(1, view()[card(bear)]?.obj?.counters(CounterKind.PowerToughness(1, 1)))
        assertEquals(0, view()[card(blinker)]?.obj?.counters(CounterKind.PowerToughness(1, 1)))
    }

    private val tutor = Cards.invent("Sweep Probe Grave Tutor", "{2}{B}", "Creature — Human Warlock", """
        When this creature enters, search your library for a creature card with mana value 2 or less, put it onto the battlefield, then shuffle. Sacrifice it at the beginning of the next end step.
    """, "1/1")

    // CR 603.7c, 608.2c, 400.7: "it" is the card found and put onto the battlefield
    @Test
    fun theDelayedSacrificeTakesTheCreatureFound() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(tutor)
        alice.library(bear, "Island")
        alice.casts(tutor)
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card(bear))))
        resolveAll()
        expect { alice.battlefield has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.battlefield has tutor
        }
    }

    private val augur = Cards.invent("Sweep Probe Augur", "{2}{G}", "Creature — Elf Shaman", """
        When this creature enters, reveal the top card of your library. If it's a creature card, put it onto the battlefield. Sacrifice it at the beginning of the next end step.
    """, "1/1")

    // CR 603.7c, 608.2c: "it" is the revealed creature put onto the battlefield
    @Test
    fun theDelayedSacrificeTakesTheRevealedCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(augur)
        alice.library(bear)
        alice.casts(augur)
        resolveAll()
        expect { alice.battlefield has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.battlefield has augur
        }
    }

    private val pyromancer = Cards.invent("Sweep Probe Pyre Caller", "{2}{R}", "Creature — Human Shaman", """
        When this creature enters, exile the top card of your library. If it's a creature card, put it onto the battlefield. It gains haste until end of turn.
    """, "1/1")

    // CR 608.2c, 400.7: haste goes to the creature put onto the battlefield, not the source
    @Test
    fun hasteGoesToTheExiledCreaturePutOntoTheBattlefield() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(pyromancer)
        alice.library(bear)
        alice.casts(pyromancer)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear hasKeyword Keyword.Haste.kind
            pyromancer lacksKeyword Keyword.Haste.kind
        }
    }

    private val scout = Cards.invent("Sweep Probe Pathscout", "{2}{G}", "Creature — Elf Scout", """
        When this creature enters, look at the top four cards of your library. You may put a creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order. It gains haste until end of turn.
    """, "1/1")

    // CR 608.2c, 400.7: "it" after the rest is put away is still the creature put onto the battlefield
    @Test
    fun hasteGoesToTheCreaturePutFromAmongTheTopCards() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(scout)
        alice.library("Island", bear, "Island", "Island")
        alice.casts(scout)
        resolveAll()
        while (pending != null) {
            when (pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card(bear))))
                else -> break
            }
            resolveAll()
        }
        expect {
            alice.battlefield has bear
            bear hasKeyword Keyword.Haste.kind
            scout lacksKeyword Keyword.Haste.kind
        }
    }

    private val drummer = Cards.invent("Sweep Probe War Drummer", "{2}{R}", "Creature — Goblin Warrior", """
        When this creature enters, you may put a creature card from your hand onto the battlefield. It gains haste until end of turn.
    """, "1/1")

    // CR 608.2c, 400.7: haste goes to the creature put from the hand
    @Test
    fun hasteGoesToTheCreaturePutFromTheHand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(drummer, bear)
        alice.casts(drummer)
        resolveAll()
        while (pending != null) {
            when (pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card(bear))))
                else -> break
            }
            resolveAll()
        }
        expect {
            alice.battlefield has bear
            bear hasKeyword Keyword.Haste.kind
            drummer lacksKeyword Keyword.Haste.kind
        }
    }

    private val raider = Cards.invent("Sweep Probe Raider", "{2}{R}", "Creature — Human Warrior",
        "Whenever this creature attacks, defending player loses 2 life.", "2/2")

    // CR 508.5, 608.2h: removed from combat before its trigger resolves, it still names the player it was attacking (of two opponents)
    @Test
    fun defendingPlayerOfAnAttackerThatLeftIsThePlayerItAttacked() = scenario(players = 3) {
        alice.battlefield(raider)
        bob.hand(murder)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(raider, defender = "carol")
        bob.castsWithoutCosts(murder, target = raider)
        resolve()
        expect { alice.graveyard has raider }
        resolveAll()
        expect {
            carol.life shouldBe 18
            bob.life shouldBe 20
        }
    }
}
