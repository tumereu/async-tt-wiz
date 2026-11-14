package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The MH3 cards of chunk 08, played: the invented names carry the oracle text of Cranial Ram, Inventor's Axe, Drossclaw, Phlage, Titan of
 * Fire's Fury, Arena of Glory, Volatile Stormdrake, Tamiyo Meets the Story Circle, Tamiyo, Inquisitive Student, Expel the Unworthy, Orim's
 * Chant, Wastescape Battlemage, Quest for the Necropolis, Reckless Pyrosurfer and The Necrobloom.
 */
class Mh3Chunk08EquipEscapeKickerLandfallScenariosTest {
    private val ram = Cards.invent("Cranebone Butter", "{B}{R}", "Artifact — Equipment", """
        Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
        Equipped creature gets +X/+1, where X is the number of artifacts you control.
        Equip {2}
    """)
    private val axe = Cards.invent("Tinkerer's Hatchet", "{R}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, you get {E}{E} (two energy counters).
        When this Equipment enters, attach it to target creature you control.
        Equipped creature gets +2/+0.
        Equip—Pay {E}{E}.
    """)
    private val dross = Cards.invent("Slagtalon", "{1}{B}", "Artifact — Equipment", """
        Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
        Equipped creature gets +1/+1.
        Whenever equipped creature attacks, each opponent loses 1 life.
        Equip {2}
    """)
    private val phlage = Cards.invent("Pyrrhax, Titan of Ember's Wrath", "{1}{R}{W}", "Legendary Creature — Elder Giant", """
        When Pyrrhax enters, sacrifice it unless it escaped.
        Whenever Pyrrhax enters or attacks, it deals 3 damage to any target and you gain 3 life.
        Escape—{R}{R}{W}{W}, Exile five other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)
    """, "6/6")
    private val arena = Cards.invent("Coliseum of Valor", null, "Land", """
        This land enters tapped unless you control a Mountain.
        {T}: Add {R}.
        {R}, {T}, Exert this land: Add {R}{R}. If any of that mana is spent on a creature spell, it gains haste until end of turn. (An exerted permanent won't untap during your next untap step.)
    """)
    private val drake = Cards.invent("Fickle Squalldrake", "{1}{U}", "Creature — Drake", """
        Flying, hexproof from activated and triggered abilities
        When this creature enters, exchange control of this creature and target creature an opponent controls. If you do, you get {E}{E}{E}{E}, then sacrifice that creature unless you pay an amount of {E} equal to its mana value.
    """, "3/2")
    private val saga = Cards.invent("Moonfolk Hears the Tale", "{1}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Until your next turn, whenever a creature attacks you or a planeswalker you control, it gets -2/-0 until end of turn.
        II — Discard any number of cards, then investigate twice for each card discarded this way.
        III — Shuffle up to three target cards from your graveyard into your library.
    """)
    private val expel = Cards.invent("Banish the Undeserving", "{1}{W}", "Sorcery", """
        Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
        Choose target creature with mana value 3 or less. If this spell was kicked, instead choose target creature. Exile the chosen creature, then its controller gains life equal to its mana value.
    """)
    private val chant = Cards.invent("Velma's Hush", "{W}", "Instant", """
        Kicker {W} (You may pay an additional {W} as you cast this spell.)
        Target player can't cast spells this turn. If this spell was kicked, creatures can't attack this turn.
    """)
    private val battlemage = Cards.invent("Blightfield Warmage", "{1}{C}", "Creature — Eldrazi Wizard", """
        Kicker {G} and/or {1}{U}
        When you cast this spell, if it was kicked with its {G} kicker, exile target artifact or enchantment an opponent controls.
        When you cast this spell, if it was kicked with its {1}{U} kicker, return target creature an opponent controls to its owner's hand.
    """, "2/2")
    private val quest = Cards.invent("Quest for the Bone Halls", "{B}", "Enchantment", """
        Landfall — Whenever a land you control enters, put a quest counter on this enchantment.
        {5}{B}, Sacrifice this enchantment: Put target creature card from a graveyard onto the battlefield under your control. This ability costs {1} less to activate for each quest counter on this enchantment. Activate only as a sorcery.
    """)
    private val surfer = Cards.invent("Heedless Flamerider", "{1}{R}", "Creature — Human Scout", """
        Haste
        Landfall — Whenever a land you control enters, this creature gains battle cry until end of turn. (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn. Each instance of battle cry triggers separately.)
    """, "2/2")
    private val bloom = Cards.invent("The Gravebloom", "{1}{W}{B}{G}", "Legendary Creature — Plant", """
        Landfall — Whenever a land you control enters, create a 0/1 green Plant creature token. If you control seven or more lands with different names, create a 2/2 black Zombie creature token instead.
        Land cards in your graveyard have dredge 2. (You may return a land card from your graveyard to your hand and mill two cards instead of drawing a card.)
    """, "2/7")

    private val scholar = Cards.invent("Kiyome, Learned Sage", "{3}{U}", "Legendary Planeswalker — Tamiyo", """
        [+2]: Until your next turn, whenever a creature attacks you or a planeswalker you control, it gets -1/-0 until end of turn.
        [−3]: Return target instant or sorcery card from your graveyard to your hand. If it's a green card, add one mana of any color.
        [−7]: Draw cards equal to half the number of cards in your library, rounded up. You get an emblem with "You have no maximum hand size."
    """, loyalty = "2")
    private val snap = Cards.invent("Sprouting Snap", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val flats = Cards.invent("Hollowstone Flats", null, "Land", "{T}: Add {C}.")
    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")
    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val pest = Cards.invent("Ember Pest", "{1}{R}", "Creature — Lizard", "", "2/1")
    private val ogre = Cards.invent("Big Ogre", "{4}{R}", "Creature — Ogre", "", "4/4")
    private val anvil = Cards.invent("Rust Anvil", "{3}", "Artifact", "")
    private val sling = Cards.invent("Pebble Sling", "{2}", "Artifact", "{1}, {T}: Pebble Sling deals 1 damage to target creature.")

    // CR 702.92a: the Germ gets +X/+1 with X the artifacts its controller controls (the Equipment and another artifact: 2), as it applies
    @Test
    fun livingWeaponGermCountsArtifacts() = scenario {
        alice.battlefield("Swamp", "Mountain", anvil)
        alice.hand(ram)
        alice.casts(ram)
        resolveAll()
        expect {
            "Phyrexian Germ Token".power shouldBe 2
            "Phyrexian Germ Token".toughness shouldBe 1
        }
    }

    // CR 702.6a, 107.14: the energy its own trigger gave pays the equip cost
    @Test
    fun equipPaidWithEnergy() = scenario {
        alice.battlefield("Mountain", bear, pest)
        alice.hand(axe)
        alice.casts(axe)
        resolveAll()
        if (pending is Decision.Targets) targets(bear)
        resolveAll()
        alice.activates(axe, target = pest)
        resolveAll()
        expect {
            pest.power shouldBe 4
            bear.power shouldBe 2
            alice.counters(CounterKind.Energy) shouldBe 0
        }
    }

    // CR 508.1m, 603.2: each opponent (two of them) loses 1 life when the equipped creature attacks
    @Test
    fun equippedCreatureAttackDrainsEachOpponent() = scenario(players = 3) {
        alice.battlefield(bear, dross)
        attach(dross, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, defender = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    // CR 702.138c: cast from the hand it is sacrificed (its other enters trigger still deals 3 and gains 3); escaped it stays
    @Test
    fun sacrificedUnlessItEscaped() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", "Mountain")
        alice.hand(phlage)
        alice.casts(phlage)
        allPass()
        resolveAll()
        if (pending is Decision.Targets) targets("bob")
        resolveAll()
        expect {
            alice.graveyard has phlage
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun escapedItStays() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains")
        alice.graveyard(phlage, "Island", "Island", "Swamp", "Swamp", "Forest")
        alice.casts(phlage)
        allPass()
        resolveAll()
        if (pending is Decision.Targets) targets("bob")
        resolveAll()
        expect {
            alice.battlefield has phlage
            bob.life shouldBe 17
            alice.life shouldBe 23
            alice.exile.size shouldBe 5
        }
    }

    // CR 106.6a: a creature spell paid with the exerted land's mana gains haste; CR 701.43c: the land doesn't untap in the next untap step
    @Test
    fun exertedManaGivesHaste() = scenario {
        alice.battlefield("Mountain", arena)
        alice.hand(pest)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.activates("Mountain")
        alice.activates(arena, ability = 1)
        alice.casts(pest)
        allPass()
        expect { pest hasKeyword Keyword.Haste.kind }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect {
            arena.tapped shouldBe true
            "Mountain".tapped shouldBe false
        }
    }

    // CR 702.11d: an opponent's activated ability can't target it, a spell can; CR 701.12b: after the exchange the controller gets four energy
    // and keeps the taken creature by paying its mana value (5) in energy only if able: with 4 it is sacrificed
    @Test
    fun hexproofFromAbilitiesAndExchange() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Swamp")
        alice.hand(drake)
        carol.battlefield(ogre)
        bob.battlefield(sling, "Mountain", "Mountain")
        alice.casts(drake)
        allPass()
        resolveAll()
        if (pending is Decision.Targets) targets(ogre)
        resolveAll()
        if (pending is Decision.May || pending is Decision.ManaPayment) error("can't pay 5 energy with 4: $pending")
        expect {
            drake.controller shouldBe carol.id
            alice.counters(CounterKind.Energy) shouldBe 4
            carol.graveyard has ogre
        }
    }

    @Test
    fun cantBeTargetedByActivatedAbilities() = scenario {
        alice.battlefield(drake)
        bob.battlefield(sling, "Mountain", "Mountain")
        bob.hand(cinder)
        turn(number = 2, active = bob)
        assertFailsWith<IllegalArgumentException> { bob.activates(sling, target = drake) }
        bob.casts(cinder, target = drake)
        allPass()
        expect { alice.graveyard has drake }
    }

    // CR 714.3b, 701.16a: chapter II discards two cards and investigates twice for each: four Clues
    @Test
    fun discardsAndInvestigatesTwiceEach() = scenario {
        alice.battlefield(saga, counters = listOf(CounterKind.Lore to 1))
        alice.hand(bear, pest, ogre)
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        alice.chooses(bear, pest)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has pest
            tokens("Clue Token").size shouldBe 4
        }
    }

    // CR 702.33d: unkicked only a creature with mana value 3 or less can be chosen; CR 608.2h: its controller gains life equal to its mana value
    @Test
    fun unkickedChoosesASmallCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(expel)
        bob.battlefield(pest, ogre)
        alice.casts(expel, target = pest, kicked = 0)
        allPass()
        expect {
            bob.exile has pest
            bob.life shouldBe 22
        }
    }

    @Test
    fun kickedChoosesAnyCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(expel)
        bob.battlefield(pest, ogre)
        alice.casts(expel, target = ogre, kicked = 1)
        allPass()
        expect {
            bob.exile has ogre
            bob.life shouldBe 25
        }
    }

    // CR 101.2: the target player can't cast spells this turn; kicked, no creature can attack this turn
    @Test
    fun kickedChantStopsSpellsAndAttacks() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(chant)
        bob.hand(cinder)
        bob.battlefield("Mountain")
        alice.casts(chant, target = "bob", kicked = 1)
        allPass()
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts(cinder, target = bear) }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear) }
    }

    // CR 702.33f: kicked only with its {1}{U} kicker, only the bounce trigger triggers; with both, both do
    @Test
    fun eachKickerCostHasItsOwnTrigger() = scenario {
        alice.battlefield(flats, "Island", "Island", "Island")
        alice.hand(battlemage)
        bob.battlefield(anvil, ogre)
        alice.casts(battlemage)
        answer(Answer.YesNo(false))
        answer(Answer.YesNo(true))
        paySuggested()
        resolveAll()
        expect {
            bob.hand has ogre
            bob.battlefield has anvil
        }
    }

    @Test
    fun bothKickersBothTriggers() = scenario {
        alice.battlefield(flats, "Forest", "Island", "Island", "Island")
        alice.hand(battlemage)
        bob.battlefield(anvil, ogre)
        alice.casts(battlemage, kicked = 1)
        resolveAll()
        expect {
            bob.hand has ogre
            bob.exile has anvil
            alice.battlefield has battlemage
        }
    }

    // CR 601.2f: two quest counters make {5}{B} cost {3}{B}; CR 110.2a: the opponent's creature card comes under your control
    @Test
    fun questReturnsAnOpponentsCreature() = scenario {
        alice.battlefield(quest, counters = listOf(CounterKind.Named("quest") to 1))
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand("Swamp")
        bob.graveyard(ogre)
        alice.plays("Swamp")
        resolveAll()
        alice.activates(quest, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            alice.graveyard has quest
        }
    }

    // CR 702.91a: battle cry gained from landfall triggers on its attack: the other attacker gets +1/+0
    @Test
    fun landfallGivesBattleCry() = scenario {
        alice.battlefield(surfer, bear)
        alice.hand("Mountain")
        alice.plays("Mountain")
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(surfer, bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            surfer.power shouldBe 2
        }
    }

    // CR 702.52a: dredge granted to a land card in the graveyard replaces the draw; CR 614.1a: fewer than seven differently named lands: a Plant
    @Test
    fun grantedDredgeReplacesTheDraw() = scenario {
        alice.battlefield(bloom)
        alice.graveyard("Forest")
        alice.library("Island", "Swamp", "Plains")
        bob.library("Island")
        turn(number = 2, active = alice, step = TurnStep.Upkeep)
        advanceTo(TurnStep.Draw)
        if (pending is Decision.ChooseOption || pending is Decision.May || pending is Decision.ChooseObjects) {
            when (val d = pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseObjects -> alice.chooses("Forest")
                is Decision.ChooseOption -> answer(Answer.Option(d.options.first { it.contains("redge") }))
                else -> {}
            }
        }
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.graveyard has "Island"
            alice.graveyard has "Swamp"
            alice.library.size shouldBe 1
        }
    }

    // CR 400.7: "if it's a green card" looks at the returned card in the hand: green, one mana of any colour is added; not green, none
    @Test
    fun returnedGreenCardAddsMana() = scenario {
        alice.battlefield(scholar, counters = listOf(CounterKind.Loyalty to 5))
        alice.graveyard(snap)
        alice.activates(scholar, ability = 1, target = snap)
        resolveAll()
        (pending as? Decision.ChooseColor)?.let { answer(Answer.ColorChoice(arcanum.api.Color.Blue)) }
        resolveAll()
        expect {
            alice.hand has snap
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun returnedRedCardAddsNoMana() = scenario {
        alice.battlefield(scholar, counters = listOf(CounterKind.Loyalty to 5))
        alice.graveyard(cinder)
        alice.activates(scholar, ability = 1, target = cinder)
        resolveAll()
        expect {
            alice.hand has cinder
            alice.pool.size shouldBe 0
        }
    }
}
