package arcanum.engine.sets.one

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 14 in play: a Vehicle with an oil counter (CR 614.1c, 122.1, 702.122), equipped as a condition
 * (CR 301.5), a mana ability for a land that entered this turn (CR 605.1a, 400.7), a land copying an artifact of mana value X
 * (CR 707.2, 107.3a), additional costs (CR 118.8, 601.2b), "can't be blocked by" (CR 509.1b), cost changes (CR 601.2f), countering
 * by mana value (CR 701.6) and Mite tokens (CR 111.1).
 */
class OneChunk14VehiclesCostsAndTokensScenariosTest {
    private val oil = CounterKind.Named("oil")
    private val strider = Cards.invent("Meldfield Strider", "{4}{U}", "Artifact — Vehicle", """
        Vigilance
        This Vehicle enters with an oil counter on it.
        Remove an oil counter from this Vehicle: It becomes an artifact creature until end of turn.
        Crew 3
    """, "5/5")
    private val ogre = Cards.invent("Hillock Ogre", "{3}{R}", "Creature — Ogre", "", "3/3")
    private val lampbearer = Cards.invent("Leonin Lampbearer", "{2}{W}", "Creature — Cat Rebel", """
        Ward {2}
        As long as this creature is equipped, it gets +1/+1.
    """, "3/2")
    private val blade = Cards.invent("Copper Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +0/+0.\nEquip {1}")
    private val mirrow = Cards.invent("Mirrow", null, "Land — Sphere", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Activate only if this land entered this turn.
    """)
    private val hollows = Cards.invent("The Mycosynth Hollows", null, "Land — Sphere", """
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
        {X}, {T}: This land becomes a copy of target nontoken artifact you control with mana value X.
    """)
    private val gizmo = Cards.invent("Brass Gizmo", "{2}", "Artifact", "")
    private val engine = Cards.invent("Iron Engine", "{3}", "Artifact", "")
    private val charm = Cards.invent("Brass Charm", "{0}", "Artifact", "")
    private val glare = Cards.invent("Annihilating Gaze", "{B}", "Sorcery", """
        As an additional cost to cast this spell, pay {4} or sacrifice an artifact or creature.
        Destroy target creature or planeswalker.
    """)
    private val sacrifice = Cards.invent("Ishmar's Sacrifice", "{1}{R}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature with mana value X.
        Ishmar's Sacrifice deals X damage divided as you choose among any number of target creatures.
    """)
    private val thrill = Cards.invent("Thrill of Fortune", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards.
    """)
    private val engulfer = Cards.invent("Skyscorch Engulfer", "{5}{G}", "Creature — Phyrexian Beast", """
        Reach, trample
        This creature can't be blocked by creatures with flying.
    """, "6/5")
    private val ravager = Cards.invent("Cinderscorch Ravager", "{4}{R}{G}", "Creature — Phyrexian Warrior", """
        This spell costs {1} less to cast for each permanent you control with oil counters on it.
        Vigilance
        When this creature enters, it deals 1 damage to each creature your opponents control.
    """, "5/5")
    private val vanish = Cards.invent("Vanish into Aeons", "{2}{W}", "Instant", """
        This spell costs {3} more to cast if it targets a creature.
        Exile target nonland permanent.
    """)
    private val misdeed = Cards.invent("Minor Misdeed", "{U}", "Instant", "Counter target spell with mana value 1 or less.")
    private val sprite = Cards.invent("Ember Sprite", "{R}", "Creature — Elemental", "", "1/1")
    private val imp = Cards.invent("Cinder Imp", "{1}{R}", "Creature — Imp", "", "2/2")
    private val herdsman = Cards.invent("Basilica Herdsman", "{3}{W}{W}", "Creature — Phyrexian Angel", """
        Flying
        When this creature enters, create two 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and "This token can't block."
    """, "3/3")
    private val cinderforger = Cards.invent("Cinderforger", "{1}{B}{R}", "Creature — Phyrexian Beast", """
        When this creature enters, create a 1/1 red Phyrexian Goblin creature token.
        Whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
        Remove three oil counters from this creature: Exile the top card of your library. You may play that card this turn.
    """, "2/3")
    private val bolt = Cards.invent("Scorch Bolt", "{R}", "Instant", "Scorch Bolt deals 3 damage to target creature.")
    private val rat = Cards.invent("Scrap Vole", "{B}", "Creature — Rat", "", "1/1")
    private val bird = Cards.invent("Thornwing Bird", "{1}{G}", "Creature — Bird", "Flying", "1/1")
    private val bear = Cards.invent("Pinecrest Boar", "{1}{G}", "Creature — Bear", "", "2/2")
    private val pup = Cards.invent("Quill Pup", "{1}", "Creature — Dog", "", "1/1")
    private val shrew = Cards.invent("Moss Shrew", "{1}", "Creature — Rodent", "", "1/1")

    // CR 614.1c, 122.1, 701.10: enters with an oil counter; removing it makes the Vehicle a creature until end of turn
    @Test
    fun vehicleEntersWithOilAndBecomesACreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(strider)
        alice.casts(strider)
        resolveAll()
        expect {
            strider.counters(oil) shouldBe 1
            assertTrue(CardType.Creature !in strider.types)
        }
        alice.activates(strider, ability = 0)
        resolveAll()
        expect {
            strider.counters(oil) shouldBe 0
            assertTrue(CardType.Creature in strider.types)
        }
    }

    // CR 702.122a: crew 3 taps creatures with total power 3 or more
    @Test
    fun crewThreeNeedsThreePower() = scenario {
        alice.battlefield(strider, ogre)
        alice.activates(strider, ability = 1, choosing = listOf(ogre))
        resolveAll()
        expect {
            assertTrue(CardType.Creature in strider.types)
            ogre.tapped shouldBe true
        }
    }

    // CR 301.5, 613.4c: +1/+1 only while equipped
    @Test
    fun equippedLampbearerGetsBigger() = scenario {
        alice.battlefield(lampbearer, blade)
        expect { lampbearer.power shouldBe 3 }
        attach(blade, lampbearer)
        expect {
            lampbearer.power shouldBe 4
            lampbearer.toughness shouldBe 3
        }
    }

    // CR 605.1a, 400.7: the any-colour ability works only for a land that entered this turn (an audit asked whether "entered this turn"
    // is read for the land itself: it is)
    @Test
    fun mirrowAnyColourOnlyTheTurnItEntered() = scenario {
        alice.hand(mirrow)
        alice.plays(mirrow)
        alice.activates(mirrow, ability = 1)
        answer(Answer.ColorChoice(Color.Red))
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun mirrowAnyColourRefusedLaterThanTheTurnItEntered() = scenario {
        alice.battlefield(mirrow)
        assertFailsWith<IllegalArgumentException> { alice.activates(mirrow, ability = 1) }
        alice.activates(mirrow, ability = 0)
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 707.2, 107.3a: X = 2 copies an artifact of mana value 2 only
    @Test
    fun hollowsCopiesAnArtifactWithManaValueX() = scenario {
        alice.battlefield(hollows, gizmo, "Plains", "Plains")
        alice.activates(hollows, ability = 2, target = gizmo, x = 2)
        resolveAll()
        expect { assertTrue(CardType.Artifact in hollows.types) }
    }

    @Test
    fun hollowsCannotTargetAnArtifactOfAnotherManaValue() = scenario {
        alice.battlefield(hollows, engine, "Plains", "Plains")
        assertFailsWith<IllegalArgumentException> { alice.activates(hollows, ability = 2, target = engine, x = 2) }
    }

    // CR 118.8, 601.2b: pay {4} instead of sacrificing
    @Test
    fun glarePaidWithFourMana() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", gizmo)
        alice.hand(glare)
        bob.battlefield(ogre)
        alice.casts(glare, target = ogre, option = "Pay")
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            gizmo.zone shouldBe Zone.Battlefield
        }
    }

    // CR 107.3a, 601.2b: X announced as 2; the artifact of mana value 2 is the one sacrificed; 2 damage divided over two creatures (CR 601.2d)
    @Test
    fun sacrificeWithManaValueXDividesXDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", gizmo, engine)
        alice.hand(sacrifice)
        bob.battlefield(pup, shrew)
        alice.casts(sacrifice, targets = listOf(pup, shrew), x = 2)
        answer(Answer.Division(listOf(Share(target(pup), 1), Share(target(shrew), 1))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            gizmo.zone shouldBe Zone.Graveyard
            engine.zone shouldBe Zone.Battlefield
            bob.graveyard.size shouldBe 2
        }
    }

    // CR 107.3a, 115.1: X = 0 sacrifices a mana value 0 permanent and needs no target
    @Test
    fun sacrificeWithManaValueZeroNeedsNoTargets() = scenario {
        alice.battlefield("Mountain", "Mountain", charm, engine)
        alice.hand(sacrifice)
        alice.casts(sacrifice, x = 0)
        resolveAll()
        expect {
            charm.zone shouldBe Zone.Graveyard
            engine.zone shouldBe Zone.Battlefield
            sacrifice.zone shouldBe Zone.Graveyard
        }
    }

    // CR 118.8: the discard is paid as the spell is cast
    @Test
    fun thrillDiscardsThenDraws() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(thrill, gizmo)
        alice.library("Forest", "Forest", "Forest")
        alice.casts(thrill)
        resolveAll()
        expect {
            gizmo.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 2
        }
    }

    // CR 509.1b: a flying creature can't block it; a creature without flying can
    @Test
    fun engulferCantBeBlockedByFliers() = scenario {
        alice.battlefield(engulfer)
        bob.battlefield(bird)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(engulfer)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 14
            bird.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun engulferCanBeBlockedByGroundCreatures() = scenario {
        alice.battlefield(engulfer)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(engulfer)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to engulfer)
        expect { engulfer.zone shouldBe Zone.Battlefield }
    }

    // CR 601.2f: two permanents with oil counters make it cost {2}{R}{G}; with none it needs all six mana
    @Test
    fun ravagerCostsLessForOilPermanents() = scenario {
        alice.battlefield("Mountain", "Forest", "Plains", "Plains")
        alice.battlefield(strider, ogre, counters = listOf(oil to 1))
        alice.hand(ravager)
        bob.battlefield(pup)
        alice.casts(ravager)
        resolveAll()
        expect {
            ravager.zone shouldBe Zone.Battlefield
            pup.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun ravagerWithoutOilPermanentsCostsFull() = scenario {
        alice.battlefield("Mountain", "Forest", "Plains", "Plains")
        alice.battlefield(strider, ogre)
        alice.hand(ravager)
        alice.casts(ravager)
        resolveAll()
        expect { ravager.zone shouldBe Zone.Stack }
    }

    // CR 601.2f: {3} more only when the spell targets a creature
    @Test
    fun vanishCostsThreeMoreAgainstACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(vanish)
        bob.battlefield(ogre)
        alice.casts(vanish, target = ogre)
        resolveAll()
        expect { vanish.zone shouldBe Zone.Stack }
    }

    @Test
    fun vanishCostsNormalAgainstANonCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(vanish)
        bob.battlefield(gizmo)
        alice.casts(vanish, target = gizmo)
        resolveAll()
        expect { gizmo.zone shouldBe Zone.Exile }
    }

    @Test
    fun vanishAgainstACreatureResolvesWithSixMana() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(vanish)
        bob.battlefield(ogre)
        alice.casts(vanish, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }

    // CR 701.6, 202.3: mana value 1 is countered, mana value 2 isn't a legal target
    @Test
    fun misdeedCountersAManaValueOneSpell() = scenario {
        alice.battlefield("Mountain")
        alice.hand(sprite)
        bob.battlefield("Island")
        bob.hand(misdeed)
        alice.casts(sprite)
        alice.passes()
        bob.casts(misdeed, target = sprite)
        resolveAll()
        expect { sprite.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun misdeedCannotTargetAManaValueTwoSpell() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(imp)
        bob.battlefield("Island")
        bob.hand(misdeed)
        alice.casts(imp)
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts(misdeed, target = imp) }
    }

    // CR 111.1, 702.164a: two Mite tokens with toxic
    @Test
    fun herdsmanMakesTwoMites() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(herdsman)
        alice.casts(herdsman)
        resolveAll()
        expect {
            assertEquals(2, alice.battlefield.names.count { it == "Phyrexian Mite Token" })
            card("Phyrexian Mite Token") hasKeyword Keyword.Toxic.Kind
        }
    }

    // CR 700.4, 122.1: the oil counter comes from another creature dying; three of them exile and let her play the top card
    @Test
    fun cinderforgerMakesATokenAndCollectsOil() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", "Mountain", rat)
        alice.hand(cinderforger, bolt)
        alice.library("Forest", "Forest")
        alice.casts(cinderforger)
        resolveAll()
        expect { assertEquals(1, alice.battlefield.names.count { it == "Phyrexian Goblin Token" }) }
        alice.casts(bolt, target = rat)
        resolveAll()
        expect { cinderforger.counters(oil) shouldBe 1 }
    }

    @Test
    fun cinderforgerSpendsThreeOilToPlayTheTopCard() = scenario {
        alice.battlefield(cinderforger, counters = listOf(oil to 3))
        alice.library("Forest", "Swamp")
        alice.activates(cinderforger, ability = 0)
        resolveAll()
        expect {
            cinderforger.counters(oil) shouldBe 0
            alice.exile has "Forest"
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }
}
