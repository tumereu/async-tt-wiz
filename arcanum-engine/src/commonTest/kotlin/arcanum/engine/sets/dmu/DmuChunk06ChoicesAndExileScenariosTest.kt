package arcanum.engine.sets.dmu

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.fin.choosePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The DMU cards of chunk 06 that give the engine something new, played: the invented names carry the oracle text of Jaya, Fiery
 * Negotiator, Sol'Kanar the Tainted, Stenn, Paranoid Partisan, Tail Swipe, Quirion Beastcaller, Karn, Living Legacy, Cut Down, The Raven
 * Man, Academy Loremaster, Chaotic Transformation, Golden Argosy, Jodah, the Unifier and Temporary Lockdown.
 */
class DmuChunk06ChoicesAndExileScenariosTest {
    private val bear = Cards.invent("Chunk Six Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val elk = Cards.invent("Chunk Six Test Elk", "{2}{G}", "Creature — Elk", "", "2/3")
    private val ogre = Cards.invent("Chunk Six Test Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val elf = Cards.invent("Chunk Six Test Elf", "{G}", "Creature — Elf", "", "1/1")
    private val wurm = Cards.invent("Chunk Six Test Wurm", "{4}{G}", "Creature — Wurm", "", "5/5")
    private val idol = Cards.invent("Chunk Six Test Idol", "{1}", "Artifact", "")
    private val relic = Cards.invent("Chunk Six Test Relic", "{3}", "Artifact", "")
    private val jolt = Cards.invent("Chunk Six Test Jolt", "{R}", "Instant", "Chunk Six Test Jolt deals 1 damage to any target.")
    private val blast = Cards.invent("Chunk Six Test Blast", "{R}", "Instant", "Chunk Six Test Blast deals 5 damage to target creature.")
    private val shatter = Cards.invent("Chunk Six Test Shatter", "{W}", "Instant", "Destroy target enchantment.")
    private val quarry = Cards.invent("Chunk Six Test Quarry", "{0}", "Sorcery", "Create a Powerstone token.")
    private val paladin = Cards.invent("Chunk Six Test Paladin", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")
    private val hermit = Cards.invent("Chunk Six Test Hermit", "{W}", "Legendary Creature — Human Monk", "", "1/1")
    private val warlord = Cards.invent("Chunk Six Test Warlord", "{2}{W}", "Legendary Creature — Human Soldier", "", "3/3")

    private val talla = Cards.invent("Talla, Ember Envoy", "{2}{R}{R}", "Legendary Planeswalker — Talla", """
        [+1]: Create a 1/1 red Monk creature token with prowess.
        [−1]: Exile the top two cards of your library. Choose one of them. You may play that card this turn.
        [−2]: Choose target creature an opponent controls. Whenever you attack this turn, Talla deals damage equal to the number of attacking creatures to that creature.
        [−8]: You get an emblem with "Whenever you cast a red instant or sorcery spell, copy it twice. You may choose new targets for the copies."
    """, loyalty = "4")
    private val vorrak = Cards.invent("Vorrak the Tainted", "{2}{U}{B}{R}", "Legendary Creature — Elemental Demon", """
        At the beginning of your end step, choose one that hasn't been chosen —
        • Draw a card.
        • Each opponent loses 2 life and you gain 2 life.
        • Vorrak deals 3 damage to up to one other target creature or planeswalker.
        • Exile Vorrak, then return it to the battlefield under an opponent's control.
    """, "5/5")
    private val oskel = Cards.invent("Oskel, Wary Partisan", "{W}{U}", "Legendary Creature — Human Wizard", """
        As Oskel enters, choose a card type other than creature or land.
        Spells you cast of the chosen type cost {1} less to cast.
        {1}{W}{U}: Exile Oskel. Return it to the battlefield under its owner's control at the beginning of the next end step.
    """, "2/2")
    private val tailLash = Cards.invent("Tail Lash Gambit", "{G}", "Instant",
        "Choose target creature you control and target creature you don't control. If you cast this spell during your main phase, the creature you control gets +1/+1 until end of turn. Then those creatures fight each other. (Each deals damage equal to its power to the other.)")
    private val beastcaller = Cards.invent("Thicket Beastcaller", "{1}{G}", "Creature — Dryad Warrior", """
        Whenever you cast a creature spell, put a +1/+1 counter on this creature.
        When this creature dies, distribute X +1/+1 counters among any number of target creatures you control, where X is the number of +1/+1 counters on this creature.
    """, "2/2")
    private val varn = Cards.invent("Varn, Enduring Legacy", "{4}", "Legendary Planeswalker — Varn", """
        [+1]: Create a tapped Powerstone token. (It's an artifact with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
        [−1]: Pay any amount of mana. Look at that many cards from the top of your library, then put one of those cards into your hand and the rest on the bottom of your library in a random order.
        [−7]: You get an emblem with "Tap an untapped artifact you control: This emblem deals 1 damage to any target."
    """, loyalty = "4")
    private val shearDown = Cards.invent("Shear Down", "{B}", "Instant", "Destroy target creature with total power and toughness 5 or less.")
    private val crowWoman = Cards.invent("The Crow Woman", "{1}{B}", "Legendary Creature — Human Wizard", """
        At the beginning of each end step, if a player discarded a card this turn, create a 1/1 black Bird creature token with flying and "This token can't block."
        {3}{B}, {T}: Each opponent discards a card. Activate only as a sorcery.
    """, "2/1")
    private val tutor = Cards.invent("Tutor of the Spires", "{U}{U}", "Creature — Human Wizard",
        "At the beginning of each player's draw step, that player may draw an additional card. If they do, spells they cast this turn cost {2} more to cast.", "2/3")
    private val upheaval = Cards.invent("Wildflux Upheaval", "{5}{R}", "Sorcery",
        "Exile up to one target artifact, up to one target creature, up to one target enchantment, up to one target planeswalker, and/or up to one target land. For each permanent exiled this way, its controller reveals cards from the top of their library until they reveal a card that shares a card type with it, puts that card onto the battlefield, then shuffles.")
    private val barque = Cards.invent("Gilded Barque", "{4}", "Legendary Artifact — Vehicle", """
        Whenever Gilded Barque attacks, exile each creature that crewed it this turn. Return them to the battlefield tapped under their owner's control at the beginning of the next end step.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "3/6")
    private val ulmar = Cards.invent("Ulmar, the Gatherer", "{W}{U}{B}{R}{G}", "Legendary Creature — Human Wizard", """
        Legendary creatures you control get +X/+X, where X is the number of legendary creatures you control.
        Whenever you cast a legendary spell from your hand, exile cards from the top of your library until you exile a legendary nonland card with lesser mana value. You may cast that card without paying its mana cost. Put the rest on the bottom of your library in a random order.
    """, "5/5")
    private val interdiction = Cards.invent("Brief Interdiction", "{1}{W}{W}", "Enchantment",
        "When this enchantment enters, exile each nonland permanent with mana value 2 or less until this enchantment leaves the battlefield.")

    // ---- Jaya, Fiery Negotiator

    // CR 603.7b-c, 508.3d: the creature chosen now is dealt damage equal to the number of attacking creatures when Alice attacks later
    @Test
    fun tallaDealsDamageToTheChosenCreatureWhenAliceAttacks() = scenario {
        alice.battlefield(talla, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(bear, elk, elf)
        bob.battlefield(ogre, wurm)
        alice.activates(talla, ability = 2, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, elk, elf, defender = "bob")
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            wurm.view.obj?.damage shouldBe 0
            talla.counters(CounterKind.Loyalty) shouldBe 2
        }
    }

    // ---- Sol'Kanar the Tainted

    // CR 400.7, 110.2a: exiled and returned as a new permanent under the opponent Alice chooses, Carol
    @Test
    fun vorrakReturnsUnderTheChosenOpponentsControl() = scenario(players = 3) {
        alice.battlefield(vorrak)
        advanceTo(TurnStep.End)
        modes(3)
        resolveAll()
        choosePlayer(carol)
        expect {
            vorrak.zone shouldBe Zone.Battlefield
            vorrak.controller shouldBe carol.id
        }
    }

    // CR 700.2, 115.1: the damage mode with no other creature chosen; Vorrak isn't a legal target of its own ability
    @Test
    fun vorrakDamagesAnotherCreatureOnly() = scenario {
        alice.battlefield(vorrak)
        bob.battlefield(ogre)
        advanceTo(TurnStep.End)
        modes(2)
        val d = decision<Decision.Targets>()
        assertFalse(target(vorrak) in d.options)
        targets(ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    // ---- Stenn, Paranoid Partisan

    // CR 205.2a, 614.12: neither creature nor land is offered; CR 601.2f: an artifact spell costs {1} less, a creature spell doesn't
    @Test
    fun oskelReducesTheChosenCardType() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(oskel, idol, elf)
        alice.casts(oskel)
        resolveAll()
        val d = decision<Decision.ChooseCardType>()
        assertFalse(CardType.Creature in d.among)
        assertFalse(CardType.Land in d.among)
        answer(Answer.CardTypeChoice(CardType.Artifact))
        resolveAll()
        alice.casts(idol)
        resolveAll()
        expect { alice.battlefield has idol }
        alice.casts(elf)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // ---- Tail Swipe

    // CR 601.2c, 505.1: cast in Alice's main phase, her creature is 3/3: it kills the 2/3 and survives its 2 damage
    @Test
    fun tailLashInMainPhasePumpsThenFights() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(tailLash)
        bob.battlefield(elk)
        alice.casts(tailLash, targets = listOf(bear, elk))
        resolveAll()
        expect {
            elk.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.14a: in Bob's turn there is no pump: the 2/2 deals 2 to the 2/3 and dies to its 2
    @Test
    fun tailLashOutsideMainPhaseOnlyFights() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(tailLash)
        bob.battlefield(elk)
        turn(number = 2, active = bob, priority = alice)
        alice.casts(tailLash, targets = listOf(bear, elk))
        resolveAll()
        expect {
            elk.zone shouldBe Zone.Battlefield
            bear.zone shouldBe Zone.Graveyard
        }
    }

    // ---- Quirion Beastcaller

    // CR 603.10a, 601.2d: X is the counters it had as it died, divided among the creatures chosen
    @Test
    fun beastcallerDistributesItsCountersWhenItDies() = scenario {
        alice.battlefield(beastcaller, counters = listOf(CounterKind.PowerToughness(1, 1) to 3))
        alice.battlefield("Mountain", bear, elk)
        alice.hand(blast)
        alice.casts(blast, target = beastcaller)
        resolveAll()
        targets(bear, elk)
        answer(Answer.Division(listOf(arcanum.engine.decision.Share(target(bear), 2), arcanum.engine.decision.Share(target(elk), 1))))
        resolveAll()
        expect {
            beastcaller.zone shouldBe Zone.Graveyard
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            elk.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // ---- Karn, Living Legacy

    // CR 111.10h: the Powerstone enters tapped
    @Test
    fun varnCreatesATappedPowerstone() = scenario {
        alice.battlefield(varn, counters = listOf(CounterKind.Loyalty to 4))
        alice.activates(varn, ability = 0)
        resolveAll()
        expect {
            "Powerstone Token".tapped shouldBe true
            "Powerstone Token".types shouldBe listOf(CardType.Artifact)
            varn.counters(CounterKind.Loyalty) shouldBe 5
        }
    }

    // CR 106.6: Powerstone mana pays for an artifact spell
    @Test
    fun powerstoneManaPaysForAnArtifact() = scenario {
        alice.hand(quarry, idol)
        alice.casts(quarry)
        resolveAll()
        alice.casts(idol)
        resolveAll()
        expect { alice.battlefield has idol }
    }

    // CR 106.6: Powerstone mana can't pay for a nonartifact spell
    @Test
    fun powerstoneManaDoesntPayForACreature() = scenario {
        alice.hand(quarry, elf)
        alice.casts(quarry)
        resolveAll()
        alice.casts(elf)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 107.1c, 118.3: Alice pays 2, looks at the top two cards and keeps one; the other goes to the bottom
    @Test
    fun varnPaysTwoAndKeepsOneOfTwo() = scenario {
        alice.battlefield(varn, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield("Mountain", "Mountain")
        alice.library(bear, elk, "Island")
        alice.activates(varn, ability = 1)
        resolveAll()
        answer(Answer.Number(2))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        alice.chooses(elk)
        resolveAll()
        expect {
            alice.hand has elk
            alice.library.names shouldBe listOf("Island", bear)
            varn.counters(CounterKind.Loyalty) shouldBe 3
        }
    }

    // ---- Cut Down

    // CR 208.1: a 2/3 (total 5) can be destroyed
    @Test
    fun shearDownDestroysTotalFive() = scenario {
        alice.battlefield("Swamp")
        alice.hand(shearDown)
        bob.battlefield(elk)
        alice.casts(shearDown, target = elk)
        resolveAll()
        expect { elk.zone shouldBe Zone.Graveyard }
    }

    // CR 115.1: a 3/3 (total 6) isn't a legal target
    @Test
    fun shearDownCantTargetTotalSix() = scenario {
        alice.battlefield("Swamp")
        alice.hand(shearDown)
        bob.battlefield(ogre)
        assertFailsWith<IllegalArgumentException> { alice.casts(shearDown, target = ogre) }
    }

    // ---- The Raven Man

    // CR 603.4: Bob discarded this turn: a Bird at the end step
    @Test
    fun crowWomanMakesABirdAfterADiscard() = scenario {
        alice.battlefield(crowWoman, "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(elf)
        alice.activates(crowWoman)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.graveyard has elf
            alice.battlefield has "Bird Token"
        }
    }

    // CR 603.4: no discard this turn: no Bird
    @Test
    fun crowWomanMakesNoBirdWithoutADiscard() = scenario {
        alice.battlefield(crowWoman)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks "Bird Token" }
    }

    // ---- Academy Loremaster

    // CR 608.2d, 601.2f: Bob draws the extra card, then his {R} spell costs {2}{R}; Alice's own costs stay as printed (3 players)
    @Test
    fun tutorRaisesOnlyTheDrawingPlayersSpells() = scenario(players = 3) {
        alice.battlefield(tutor, "Mountain")
        alice.hand(jolt)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(blast)
        bob.library("Island", "Island", "Island")
        turn(number = 2, active = bob, step = TurnStep.Upkeep)
        advanceTo(TurnStep.Draw)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect { bob.hand.size shouldBe 3 }
        advanceTo(TurnStep.PrecombatMain)
        bob.casts(blast, target = tutor)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 601.2f: the increase is only for the player who drew; Alice casts at the printed cost in Bob's turn
    @Test
    fun tutorLeavesOtherPlayersSpellsAlone() = scenario(players = 3) {
        alice.battlefield(tutor, "Mountain")
        alice.hand(jolt)
        bob.library("Island", "Island", "Island")
        turn(number = 2, active = bob, step = TurnStep.Upkeep)
        advanceTo(TurnStep.Draw)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        bob.passes()
        carol.passes()
        alice.casts(jolt, target = "carol")
        resolveAll()
        expect { carol.life shouldBe 19 }
    }

    // ---- Chaotic Transformation

    // CR 608.2h, 110.2a, 701.20a: Bob's creature and Carol's artifact are exiled; each reveals until a card sharing a type and puts it onto the
    // battlefield under their own control
    @Test
    fun upheavalReplacesEachExiledPermanentFromItsControllersLibrary() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(upheaval)
        bob.battlefield(bear)
        bob.library("Island", wurm, "Island")
        carol.battlefield(idol)
        carol.library("Plains", relic, "Plains")
        alice.casts(upheaval, targets = listOf(idol, bear))
        while (pending is Decision.Targets) answer(Answer.Targets(emptyList())) // no enchantment, planeswalker or land
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Exile
            idol.zone shouldBe Zone.Exile
            wurm.zone shouldBe Zone.Battlefield
            wurm.controller shouldBe bob.id
            relic.zone shouldBe Zone.Battlefield
            relic.controller shouldBe carol.id
        }
    }

    // ---- Golden Argosy

    // CR 702.122, 603.7: the creature that crewed it is exiled as it attacks and returns tapped at the next end step
    @Test
    fun barqueExilesItsCrewUntilTheEndStep() = scenario {
        alice.battlefield(barque, bear)
        alice.activates(barque, choosing = listOf(bear))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(barque, defender = "bob")
        resolveAll()
        expect { bear.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
        }
    }

    // ---- Jodah, the Unifier

    // CR 701.20a, 608.2h: a legendary card of mana value 3 is passed over for one of mana value 1 (lesser than the Paladin's 2); cast free;
    // the rest go to the bottom
    @Test
    fun ulmarCastsALegendaryWithLesserManaValue() = scenario {
        alice.battlefield(ulmar, "Plains", "Plains")
        alice.hand(paladin)
        alice.library(warlord, "Island", hermit, "Swamp")
        alice.casts(paladin)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has paladin
            alice.battlefield has hermit
            alice.library.names.first() shouldBe "Swamp"
            alice.library.size shouldBe 3
        }
    }

    // ---- Temporary Lockdown

    // CR 610.3: every nonland permanent of mana value 2 or less, both players', until the enchantment leaves
    @Test
    fun interdictionExilesUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", elf)
        alice.hand(interdiction, shatter)
        bob.battlefield(bear, ogre)
        alice.casts(interdiction)
        resolveAll()
        expect {
            elf.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Exile
            ogre.zone shouldBe Zone.Battlefield
        }
        alice.casts(shatter, target = interdiction)
        resolveAll()
        expect {
            elf.zone shouldBe Zone.Battlefield
            bear.zone shouldBe Zone.Battlefield
            bear.controller shouldBe bob.id
        }
    }
}
