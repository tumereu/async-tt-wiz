package arcanum.engine.sets.mkm

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MKM chunk 14: Equipment, additional costs, evasion limits, can't be countered, conditional cost reduction. */
class MkmChunk14EquipmentAndRestrictionsScenariosTest {
    private val candlestick = Cards.invent("Pewter Candlestick", "{U}", "Artifact — Clue Equipment", """
        Equipped creature gets +1/+1 and has "Whenever this creature attacks, surveil 2." (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        {2}, Sacrifice this Equipment: Draw a card.
        Equip {2}
    """)
    private val haunch = Cards.invent("Smoked Haunch", "{W}", "Artifact — Food Equipment", """
        Equipped creature gets +2/+0.
        {2}, {T}, Sacrifice this Equipment: You gain 3 life.
        When this Equipment is put into a graveyard from the battlefield, you may pay {1}{W}. If you do, create two 1/1 white Dog creature tokens.
        Equip {2}
    """)
    private val pipe = Cards.invent("Iron Pipe", "{B}", "Artifact — Clue Equipment", """
        Equipped creature gets +2/+0.
        Whenever equipped creature dies, each opponent loses 1 life.
        {2}, Sacrifice this Equipment: Draw a card.
        Equip {2}
    """)
    private val cap = Cards.invent("Deerstalker Cap", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+2.
        Equip Detective {1}
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val strider = Cards.invent("Mausoleum Golem", "{2}", "Artifact Creature — Golem", """
        {1}: Add one mana of any color. Activate only once each turn.
        {2}, Exile this card from your graveyard: Exile target card from a graveyard.
    """, "1/3")
    private val press = Cards.invent("Press for Facts", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, sacrifice an artifact or discard a card.
        Draw two cards.
    """)
    private val avarice = Cards.invent("Spiteful Avarice", "{1}{W}{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature that dealt damage this turn.
        Draw three cards. Each opponent loses 3 life and you gain 3 life.
    """)
    private val ferryman = Cards.invent("Sneaking Ferryman", "{2}{U}", "Creature — Merfolk Advisor", """
        This creature can't be blocked as long as you've sacrificed an artifact this turn.
        Whenever this creature attacks, draw a card, then discard a card.
    """, "3/2")
    private val lookout = Cards.invent("Doorstep Lookout", "{2}{W}", "Legendary Creature — Human Scout", """
        Creatures you control with power 2 or less can't be blocked by creatures with power 3 or greater.
    """, "2/2")
    private val farewell = Cards.invent("Curt Farewell", "{1}{B}", "Instant", """
        This spell can't be countered.
        Destroy target creature or planeswalker with mana value 3 or less.
    """)
    private val cut = Cards.invent("Cut from the Dark", "{X}{B}", "Instant", """
        This spell can't be countered.
        Target creature gets -X/-X until end of turn.
    """)
    private val blast = Cards.invent("Doubtful Blast", "{4}{R}", "Sorcery", """
        This spell costs {3} less to cast if you've sacrificed an artifact this turn.
        This spell can't be countered.
        Doubtful Blast deals 4 damage to target creature.
    """)
    private val satchel = Cards.invent("Evidence Satchel", "{1}", "Artifact — Clue", "{2}, Sacrifice this artifact: Draw a card.")
    private val detective = Cards.invent("Plain Detective", "{1}{U}", "Creature — Human Detective", "", "2/2")


    // CR 301.5, 702.6a, 113.3d: +1/+1 and the granted attack trigger (surveil 2, CR 701.25a) belong to the equipped creature
    @Test
    fun candlestickGivesTheAttackSurveil() = scenario {
        alice.battlefield(candlestick, Plain.bear, "Island", "Island")
        alice.library("Island", "Swamp", "Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        alice.activates(candlestick, ability = 1, target = Plain.bear)
        allPass()
        expect {
            Plain.bear.power shouldBe 3
            Plain.bear.toughness shouldBe 3
        }
        unblocked(Plain.bear, picks = listOf("Island"))
        assertTrue(alice.graveyard.names.contains("Island"))
    }

    // CR 602.2: the Clue ability sacrifices the Equipment as a cost and draws
    @Test
    fun candlestickSacrificesToDraw() = scenario {
        alice.battlefield(candlestick, "Island", "Island")
        alice.library("Island", "Swamp")
        bob.library("Swamp", "Swamp")
        alice.activates(candlestick, ability = 0)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            candlestick.zone shouldBe Zone.Graveyard
        }
    }

    // CR 301.5: +2/+0; CR 603.10a: the leaves-the-battlefield trigger of the sacrificed Equipment; "you may pay ... If you do"
    @Test
    fun haunchSacrificedPaysForTwoDogs() = scenario {
        alice.battlefield(haunch, "Plains", "Plains", "Plains", "Plains")
        alice.library("Plains", "Plains")
        bob.library("Swamp", "Swamp")
        alice.activates(haunch, ability = 0)
        playOut(may = true)
        expect {
            alice.life shouldBe 23
            haunch.zone shouldBe Zone.Graveyard
            alice.battlefield has "Dog Token"
        }
        assertEquals(2, state.objects.values.count { o -> o.zone == Zone.Battlefield && nameOf(o) == "Dog Token" })
    }

    // CR 118.12: declining the payment makes no Dogs
    @Test
    fun haunchDecliningMakesNoDogs() = scenario {
        alice.battlefield(haunch, "Plains", "Plains", "Plains", "Plains")
        alice.library("Plains", "Plains")
        bob.library("Swamp", "Swamp")
        alice.activates(haunch, ability = 0)
        playOut(may = false)
        expect { alice.life shouldBe 23 }
        assertEquals(0, state.objects.values.count { o -> o.zone == Zone.Battlefield && nameOf(o) == "Dog Token" })
    }

    // CR 301.5: Equip attaches at sorcery speed and gives +2/+0
    @Test
    fun haunchEquipGivesPower() = scenario {
        alice.battlefield(haunch, Plain.bear, "Plains", "Plains")
        alice.activates(haunch, ability = 1, target = Plain.bear)
        allPass()
        expect {
            Plain.bear.power shouldBe 4
            Plain.bear.toughness shouldBe 2
        }
    }

    // CR 603.10a, 103.1: each opponent (two of them) loses 1 life when the equipped creature dies
    @Test
    fun pipeDrainsEachOpponentWhenTheEquippedCreatureDies() = scenario(players = 3) {
        alice.battlefield(pipe, Plain.bear, "Swamp")
        alice.hand(Plain.doom)
        attach(pipe, Plain.bear)
        expect { Plain.bear.power shouldBe 4 }
        alice.casts(Plain.doom, target = Plain.bear)
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 702.6a: "Equip Detective" can only target a Detective; the plain Equip targets any creature you control
    @Test
    fun capEquipsDetectivesCheaply() = scenario {
        alice.battlefield(cap, detective, Plain.bear, "Island", "Island", "Island", "Island")
        // the Detective is the only creature the cheap Equip offers
        alice.activates(cap, ability = 0)
        assertEquals(listOf(target(detective)), decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target(detective))))
        playOut()
        expect {
            Plain.bear.power shouldBe 2
            detective.power shouldBe 3
            detective.toughness shouldBe 4
        }
        alice.activates(cap, ability = 1, target = Plain.bear)
        allPass()
        expect {
            Plain.bear.power shouldBe 3
            Plain.bear.toughness shouldBe 4
            detective.power shouldBe 2
        }
    }

    // CR 602.5b: once each turn; the exile-from-graveyard cost lets it work from the graveyard (CR 113.6b)
    @Test
    fun striderExilesACardFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.graveyard(strider)
        bob.graveyard(Plain.bear)
        alice.activates(strider, ability = 1, target = Plain.bear)
        resolveAll()
        expect {
            strider.zone shouldBe Zone.Exile
            Plain.bear.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun striderManaAbilityOnlyOnceEachTurn() = scenario {
        alice.battlefield(strider, "Island", "Island")
        alice.activates(strider, ability = 0)
        assertFailsWith<IllegalArgumentException> { alice.activates(strider, ability = 0) }
    }

    // CR 118.8, 601.2b: the additional cost is one of two, chosen as the spell is cast
    @Test
    fun pressSacrificesAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", satchel)
        alice.hand(press, Plain.elf)
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.casts(press, option = "Sacrifice")
        playOut()
        expect {
            satchel.zone shouldBe Zone.Graveyard
            alice.hand has Plain.elf
            alice.hand.size shouldBe 3
        }
    }

    @Test
    fun pressDiscardsACard() = scenario {
        alice.battlefield("Mountain", "Mountain", satchel)
        alice.hand(press, Plain.elf)
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.casts(press, option = "Discard")
        playOut(Plain.elf)
        expect {
            satchel.zone shouldBe Zone.Battlefield
            alice.graveyard has Plain.elf
            alice.hand.size shouldBe 2
        }
    }

    // CR 118.8: only a creature that dealt damage this turn may be sacrificed; CR 103.1: each opponent loses 3
    @Test
    fun avariceNeedsACreatureThatDealtDamage() = scenario(players = 3) {
        alice.battlefield("Plains", "Swamp", "Plains", "Plains", Plain.pinger, Plain.elf)
        alice.hand(avarice)
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Swamp", "Swamp")
        carol.library("Swamp", "Swamp")
        assertFailsWith<IllegalArgumentException> { alice.casts(avarice) }
        alice.activates(Plain.pinger, target = "bob")
        resolveAll()
        alice.casts(avarice)
        playOut(Plain.pinger)
        expect {
            Plain.pinger.zone shouldBe Zone.Graveyard
            Plain.elf.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 3
            alice.life shouldBe 23
            bob.life shouldBe 16
            carol.life shouldBe 17
        }
    }

    // CR 509.1b: the creature can be blocked until an artifact has been sacrificed this turn
    @Test
    fun ferrymanCanBeBlockedWithoutASacrifice() = scenario {
        alice.battlefield(ferryman)
        alice.library("Island", "Island")
        bob.battlefield(Plain.bear)
        bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ferryman)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(Plain.bear to ferryman)
    }

    @Test
    fun ferrymanIsUnblockableAfterASacrifice() = scenario {
        alice.battlefield(ferryman, satchel, "Island", "Island")
        alice.library("Island", "Island", "Island")
        bob.battlefield(Plain.bear)
        bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ferryman)
        playOut()
        alice.activates(satchel)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(Plain.bear to ferryman) }
    }

    // CR 509.1b, 613.1f: protects each creature you control with power 2 or less from power 3 or greater blockers only
    @Test
    fun lookoutProtectsSmallCreaturesFromBigBlockers() = scenario {
        alice.battlefield(lookout, Plain.elf, Plain.giant)
        bob.battlefield(Plain.ogre, Plain.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(Plain.elf, Plain.giant)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(Plain.ogre to Plain.elf) }
        bob.blocks(Plain.bear to Plain.elf, Plain.ogre to Plain.giant)
    }

    // CR 101.2, 701.5a: a counterspell does nothing against a spell that can't be countered
    @Test
    fun farewellResistsACounterspell() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(farewell)
        bob.battlefield("Island", "Island", Plain.turtle)
        bob.hand(Plain.counterspell)
        alice.casts(farewell, target = Plain.turtle)
        alice.passes()
        bob.casts(Plain.counterspell, target = farewell)
        playOut()
        expect {
            bob.graveyard has Plain.turtle
            alice.graveyard has farewell
        }
    }

    // CR 107.3a, 101.2: X is announced; the -X/-X resolves even against a counterspell
    @Test
    fun cutResistsACounterspell() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(cut)
        bob.battlefield("Island", "Island", Plain.ogre)
        bob.hand(Plain.counterspell)
        alice.casts(cut, target = Plain.ogre, x = 2)
        alice.passes()
        bob.casts(Plain.counterspell, target = cut)
        playOut()
        expect {
            Plain.ogre.power shouldBe 1
            Plain.ogre.toughness shouldBe 1
        }
    }

    // CR 601.2f: the reduction applies only once an artifact was sacrificed this turn
    @Test
    fun blastCostsLessAfterASacrifice() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", "Island", satchel)
        alice.hand(blast)
        bob.battlefield(Plain.ogre)
        alice.library("Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.activates(satchel, paying = listOf("Island", "Island"))
        resolveAll()
        alice.casts(blast, target = Plain.ogre, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        expect { bob.graveyard has Plain.ogre }
    }

    @Test
    fun blastCostsFullWithoutASacrifice() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(blast)
        bob.battlefield(Plain.ogre)
        alice.casts(blast, target = Plain.ogre)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }
}
