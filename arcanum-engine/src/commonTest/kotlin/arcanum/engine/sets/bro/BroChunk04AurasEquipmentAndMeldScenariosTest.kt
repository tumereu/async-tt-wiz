package arcanum.engine.sets.bro

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 04 in play: Auras that grant an activated ability and a subtype, animate an artifact, change with who controls
 * the enchanted creature, or tap it for an optional payment; Equipment that follows a creature back from the graveyard, counts the
 * attachments, or equips for either of two costs; and three meld pairs: two attacking creatures (three players, the melded one entering
 * attacking a chosen player), a creature and a land at the upkeep, and a creature and an artifact by an activated ability into a
 * planeswalker that activates loyalty abilities twice a turn.
 */
class BroChunk04AurasEquipmentAndMeldScenariosTest {
    private val powerstoneReminder = "(It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")"

    private val wyrmbloodMantle = Cards.invent("Wyrmblood Mantle", "{1}{R}{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 and has flying, haste, and "{1}: This creature gets +1/+0 until end of turn." It's a Dragon in addition to its other types.
        When enchanted creature dies, return this card to its owner's hand.
    """)
    private val ironwakeKindling = Cards.invent("Ironwake Kindling", "{3}{U}", "Enchantment — Aura", """
        Enchant artifact
        When this Aura enters, draw a card.
        Enchanted artifact is a creature with base power and toughness 4/4 in addition to its other types.
    """)
    private val tyrantsLeash = Cards.invent("Tyrant's Leash", "{1}{R}", "Enchantment — Aura", """
        Enchant creature
        As long as you control enchanted creature, it gets +2/+2. Otherwise, it can't block.
    """)
    private val dimstoneFetter = Cards.invent("Dimstone Fetter", "{U}", "Enchantment — Aura", """
        Enchant artifact or creature
        When this Aura enters, you may pay {3}. If you do, tap enchanted permanent.
        Enchanted permanent doesn't untap during its controller's untap step.
    """)
    private val brashOverreach = Cards.invent("Brash Overreach", "{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+0 and has trample. (It can deal excess combat damage to the player or planeswalker it's attacking.)
        When this Aura is put into a graveyard from the battlefield, draw a card.
    """)
    private val gravehookTalon = Cards.invent("Gravehook Talon", "{2}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and has menace. (It can't be blocked except by two or more creatures.)
        Whenever a creature enters from your graveyard, you may attach this Equipment to it.
        Equip {1}{B} ({1}{B}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val ancestralExoframe = Cards.invent("Ancestral Exoframe", "{2}", "Artifact — Equipment", """
        Equipped creature gets +1/+1 for each Aura and Equipment attached to it and has ward {2}. (Whenever equipped creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val rotgiltDiadem = Cards.invent("Rotgilt Diadem", "{2}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        Whenever equipped creature dies, draw a card.
        Equip {2} or {B}
    """)
    private val loamcallerWurm = Cards.invent("Loamcaller Wurm", "{3}{G}", "Creature — Wurm",
        "{3}{G}: Put four +1/+1 counters on target land you control. It becomes a 0/0 Elemental creature with haste. It's still a land.", "3/3")

    private val varkonBack = face("Varkon, Lost to Ruin", null, "Legendary Artifact Creature — Phyrexian Artificer", """
        Whenever Varkon enters or attacks, choose three —
        • Target opponent discards two cards.
        • Varkon deals 3 damage to any target.
        • Destroy target artifact or planeswalker.
        • Creatures you control gain menace and trample until end of turn.
        • Creatures you don't control get -1/-1 until end of turn.
        • Create two tapped Powerstone tokens. $powerstoneReminder
    """.trimIndent(), "9/9", null, "BR")
    private val varkon = Cards.inventFaces("Meld:Ruin Drake Engine",
        face("Varkon, Claimed by Ruin", "{2}{B}{R}", "Legendary Creature — Phyrexian Human Artificer",
            "Whenever you attack, each opponent loses X life and you gain X life, where X is the number of attacking creatures. If Varkon, Claimed by Ruin and a creature named Ruin Drake Engine are attacking, and you both own and control them, exile them, then meld them into Varkon, Lost to Ruin. It enters tapped and attacking.",
            "3/5", null, null),
        varkonBack)
    private val ruinDrake = Cards.inventFaces("Meld:Varkon, Claimed by Ruin",
        face("Ruin Drake Engine", "{3}", "Artifact Creature — Phyrexian Dragon", "Double strike\n(Melds with Varkon, Claimed by Ruin.)", "2/2", null, null),
        varkonBack)

    private val sylvaraBack = face("Sylvara, Loam Incarnate", null, "Legendary Creature — Elemental Avatar", """
        Reach, vigilance, trample, haste
        Sylvara's power and toughness are each equal to the number of lands you control.
        When Sylvara enters, return all land cards from your graveyard to the battlefield tapped.
        {3}{G}: Put four +1/+1 counters on target land you control. It becomes a 0/0 Elemental creature with haste. It's still a land.
    """.trimIndent(), "*/*", null, "G")
    private val sylvara = Cards.inventFaces("Meld:Sylvgrove, Sanctum of Roots",
        face("Sylvara, Voice of Loam", "{1}{G}{G}", "Legendary Creature — Elemental", """
            Reach
            Whenever one or more land cards are put into your graveyard from anywhere, you gain 2 life.
            At the beginning of your upkeep, if there are four or more land cards in your graveyard and you both own and control Sylvara, Voice of Loam and a land named Sylvgrove, Sanctum of Roots, exile them, then meld them into Sylvara, Loam Incarnate.
        """.trimIndent(), "3/4", null, null),
        sylvaraBack)
    private val sylvgrove = Cards.inventFaces("Meld:Sylvara, Voice of Loam",
        face("Sylvgrove, Sanctum of Roots", null, "Legendary Land", "{T}: Add {G}.\n(Melds with Sylvara, Voice of Loam.)", null, null, null),
        sylvaraBack)

    private val orenBack = face("Oren, Wardwalker", null, "Legendary Planeswalker — Urza", """
        You may activate the loyalty abilities of Oren twice each turn rather than only once.
        [+2]: Artifact, instant, and sorcery spells you cast this turn cost {2} less to cast. You gain 2 life.
        [+1]: Draw two cards, then discard a card.
        [0]: Create two 1/1 colorless Soldier artifact creature tokens.
        [−3]: Exile target nonland permanent.
        [−10]: Artifacts and planeswalkers you control gain indestructible until end of turn. Destroy all nonland permanents.
    """.trimIndent(), null, "7", "WU")
    private val oren = Cards.inventFaces("Meld:The Brightstone and Dimstone",
        face("Oren, Lord Warden", "{1}{W}{U}", "Legendary Creature — Human Artificer", """
            Artifact, instant, and sorcery spells you cast cost {1} less to cast.
            {7}: If you both own and control Oren, Lord Warden and an artifact named The Brightstone and Dimstone, exile them, then meld them into Oren, Wardwalker. Activate only as a sorcery.
        """.trimIndent(), "2/4", null, null),
        orenBack)
    private val stones = Cards.inventFaces("Meld:Oren, Lord Warden",
        face("The Brightstone and Dimstone", "{5}", "Legendary Artifact", "{T}: Add {C}{C}.\n(Melds with Oren, Lord Warden.)", null, null, null),
        orenBack)

    private val mudBear = Cards.invent("Silt Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val rockOgre = Cards.invent("Crag Ogre", "{3}{R}", "Creature — Ogre", "", "3/3")
    private val rustedTrinket = Cards.invent("Tarnished Bauble", "{1}", "Artifact")
    private val graveRecall = Cards.invent("Grave Recall", "{1}{B}", "Sorcery", "Return target creature card from your graveyard to the battlefield.")

    private val skirmishHerald = Cards.invent("Skirmish Herald", "{2}{R}", "Creature — Goblin", "Whenever this creature attacks, create a 1/1 red Goblin creature token that's tapped and attacking.", "2/2")

    private fun Scenario.libraries() { players.forEach { it.library("Island", "Island", "Island", "Island", "Island") } }
    private fun Scenario.nameOf(name: String) = view()[card(name)]?.characteristics?.name

    // ---- Auras

    // CR 613.1d, 613.1f, 613.4c: +1/+1, flying, haste, the Dragon subtype and a granted activated ability the creature's controller activates
    @Test
    fun wyrmbloodMantleMakesADragonThatPumpsItself() = scenario {
        alice.battlefield(mudBear, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(wyrmbloodMantle)
        alice.casts(wyrmbloodMantle, target = mudBear)
        playOut()
        alice.activates(mudBear)
        playOut()
        expect {
            mudBear.power shouldBe 4
            mudBear.toughness shouldBe 3
            mudBear hasKeyword Keyword.Flying.kind
            mudBear hasKeyword Keyword.Haste.kind
            card(mudBear).view.characteristics!!.hasSubtype(Subtype("Dragon")) shouldBe true
            card(mudBear).view.characteristics!!.hasSubtype(Subtype("Bear")) shouldBe true
        }
    }

    // CR 205.1b, 613.1d, 613.4b: the artifact becomes a 4/4 artifact creature; CR 603.6a: the Aura's enters trigger draws
    @Test
    fun ironwakeKindlingAnimatesAnArtifact() = scenario {
        libraries()
        alice.battlefield(rustedTrinket, "Island", "Island", "Island", "Island")
        alice.hand(ironwakeKindling)
        alice.casts(ironwakeKindling, target = rustedTrinket)
        playOut()
        expect {
            rustedTrinket.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            rustedTrinket.power shouldBe 4
            rustedTrinket.toughness shouldBe 4
            alice.hand.size shouldBe 1
        }
    }

    // CR 611.3a: on your own creature it gets +2/+2
    @Test
    fun tyrantsLeashOnYourOwnCreaturePumpsIt() = scenario {
        alice.battlefield(mudBear, tyrantsLeash)
        attach(tyrantsLeash, mudBear)
        expect {
            mudBear.power shouldBe 4
            mudBear.toughness shouldBe 4
        }
    }

    // CR 611.3a, 509.1b: on an opponent's creature it gets nothing and can't block
    @Test
    fun tyrantsLeashOnAnOpponentsCreatureStopsItBlocking() = scenario {
        alice.battlefield(rockOgre, tyrantsLeash)
        bob.battlefield(mudBear)
        attach(tyrantsLeash, mudBear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rockOgre)
        allPass()
        expect {
            mudBear.power shouldBe 2
            mudBear.toughness shouldBe 2
        }
        assertFailsWith<IllegalArgumentException> { bob.blocks(mudBear to rockOgre) }
    }

    // CR 118.8, 603.12: paying {3} as the trigger resolves taps the enchanted permanent
    @Test
    fun dimstoneFetterPaidTapsIt() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(dimstoneFetter)
        bob.battlefield(rockOgre)
        alice.casts(dimstoneFetter, target = rockOgre)
        playOut(may = true)
        expect {
            rockOgre.tapped shouldBe true
            alice.battlefield.names.count { it == "Island" } shouldBe 4
        }
    }

    // CR 603.12: declining the payment leaves it untapped
    @Test
    fun dimstoneFetterDeclinedLeavesItUntapped() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(dimstoneFetter)
        bob.battlefield(rockOgre)
        alice.casts(dimstoneFetter, target = rockOgre)
        playOut(may = false)
        expect {
            rockOgre.tapped shouldBe false
            alice.battlefield has dimstoneFetter
        }
    }

    // ---- Equipment

    // CR 603.6a, 108.3, 701.3a: a creature returned from your graveyard gets the Equipment attached
    @Test
    fun gravehookTalonFollowsACreatureBackFromYourGraveyard() = scenario {
        alice.battlefield(gravehookTalon, "Swamp", "Swamp")
        alice.graveyard(rockOgre)
        alice.hand(graveRecall)
        alice.casts(graveRecall, target = rockOgre)
        playOut(may = true)
        expect {
            rockOgre.zone shouldBe Zone.Battlefield
            rockOgre.power shouldBe 4
            rockOgre hasKeyword Keyword.Menace.kind
        }
    }

    // CR 603.2, 404.1: a creature entering from another player's graveyard doesn't trigger it
    @Test
    fun gravehookTalonIgnoresAnotherPlayersGraveyard() = scenario {
        alice.battlefield(gravehookTalon)
        bob.battlefield("Swamp", "Swamp")
        bob.graveyard(rockOgre)
        bob.hand(graveRecall)
        turn(active = bob)
        bob.casts(graveRecall, target = rockOgre)
        playOut()
        expect {
            rockOgre.zone shouldBe Zone.Battlefield
            rockOgre.power shouldBe 3
            state.stack.isEmpty() shouldBe true
        }
    }

    // CR 613.4c: +1/+1 for each Aura and Equipment attached (itself and an Aura); CR 702.21a (ward)
    @Test
    fun ancestralExoframeCountsTheAttachments() = scenario {
        alice.battlefield(mudBear, ancestralExoframe, brashOverreach)
        attach(ancestralExoframe, mudBear)
        attach(brashOverreach, mudBear)
        expect {
            mudBear.power shouldBe 6
            mudBear.toughness shouldBe 4
            mudBear hasKeyword Keyword.Ward.Kind
            mudBear hasKeyword Keyword.Trample.kind
        }
    }

    // CR 702.6a, 601.2f, 602.2b: with only a Swamp the equip ability is paid with {B}
    @Test
    fun rotgiltDiademEquipsForB() = scenario {
        alice.battlefield(rotgiltDiadem, mudBear, "Swamp")
        alice.activates(rotgiltDiadem, target = mudBear, option = "Pay {B}")
        playOut()
        expect {
            mudBear.power shouldBe 4
            "Swamp".tapped shouldBe true
        }
    }

    // CR 702.6a, 601.2f: with two Plains it's paid with {2}
    @Test
    fun rotgiltDiademEquipsForTwo() = scenario {
        alice.battlefield(rotgiltDiadem, mudBear, "Plains", "Plains")
        alice.activates(rotgiltDiadem, target = mudBear, option = "Pay {2}")
        playOut()
        expect {
            mudBear.power shouldBe 4
            card("Plains", 0).tapped shouldBe true
            card("Plains", 1).tapped shouldBe true
        }
    }

    // CR 205.1b: "It's still a land": the target land becomes a 4/4 Elemental creature with haste and stays a Forest
    @Test
    fun loamcallerWurmAnimatesALandThatStaysALand() = scenario {
        alice.battlefield(loamcallerWurm, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(loamcallerWurm, target = "Forest")
        playOut()
        val forest = card("Forest", 0)
        expect {
            val c = view()[forest]!!.characteristics!!
            c.isType(CardType.Land) shouldBe true
            c.isType(CardType.Creature) shouldBe true
            c.hasSubtype(Subtype("Forest")) shouldBe true
            c.hasSubtype(Subtype("Elemental")) shouldBe true
            view()[forest]!!.power shouldBe 4
            view()[forest]!!.toughness shouldBe 4
        }
    }

    // ---- Meld

    // CR 603.2c, 508.1m: "whenever you attack" drains each opponent by the attackers; CR 701.42a, 712.4a: both attacking, they meld; CR
    // 508.4: the melded permanent enters tapped and attacking the player its controller chooses (Carol, though Varkon attacked Bob), and
    // its enters trigger works, but not its attack trigger
    @Test
    fun varkonAndTheDrakeAttackingMeldIntoAnAttacker() = scenario(players = 3) {
        libraries()
        alice.battlefield(varkon, ruinDrake)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(varkon), TargetRef.Player(bob.id)), Attack(card(ruinDrake), TargetRef.Player(carol.id)))))
        playOut()
        val target = decision<Decision.Targets>()
        assertEquals(setOf(TargetRef.Player(bob.id), TargetRef.Player(carol.id)), target.options.toSet())
        answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        decision<Decision.Modes>()
        answer(Answer.Modes(listOf(3, 4, 5)))
        playOut()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 22
            nameOf(varkon) shouldBe "Varkon, Lost to Ruin"
            varkon.tapped shouldBe true
            alice.battlefield.names.count { it == "Powerstone Token" } shouldBe 2
        }
        assertEquals(listOf(TargetRef.Player(carol.id)), state.combat!!.attackers.filter { it.id == card(varkon) }.map { it.target })
    }

    // CR 701.42a: with the drake not attacking nothing melds; the drain counts the one attacker
    @Test
    fun varkonAttackingAloneDoesNotMeld() = scenario(players = 3) {
        libraries()
        alice.battlefield(varkon, ruinDrake)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(varkon)
        playOut()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 21
            nameOf(varkon) shouldBe "Varkon, Claimed by Ruin"
            alice.battlefield has ruinDrake
        }
    }

    // CR 603.4, 701.42a, 712.4a: four land cards in the graveyard: at the upkeep they meld; the melded creature (2/2 with the two Plains,
    // CR 604.3) returns the lands tapped and counts them
    @Test
    fun sylvaraMeldsAtTheUpkeepWithFourLandsInTheGraveyard() = scenario {
        libraries()
        alice.battlefield(sylvara, sylvgrove, "Plains", "Plains")
        alice.graveyard("Forest", "Forest", "Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        expect {
            nameOf(sylvara) shouldBe "Sylvara, Loam Incarnate"
            alice.battlefield.names.count { it == "Forest" } shouldBe 4
            (0..3).all { card("Forest", it).tapped } shouldBe true
            alice.graveyard.size shouldBe 0
            sylvara.power shouldBe 6
            sylvara.toughness shouldBe 6
        }
    }

    // CR 603.4: three land cards aren't enough: the trigger doesn't happen
    @Test
    fun sylvaraDoesNotMeldWithThreeLandsInTheGraveyard() = scenario {
        libraries()
        alice.battlefield(sylvara, sylvgrove)
        alice.graveyard("Forest", "Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        expect {
            nameOf(sylvara) shouldBe "Sylvara, Voice of Loam"
            alice.battlefield has sylvgrove
            alice.graveyard.size shouldBe 3
        }
    }

    // CR 701.42a, 712.4a, 602.5d: the activated meld makes a planeswalker with its printed loyalty (CR 306.5b); CR 606.3: its loyalty
    // abilities may be activated twice this turn, not three times
    @Test
    fun orenMeldsIntoAPlaneswalkerThatActivatesTwice() = scenario {
        libraries()
        alice.battlefield(oren, stones, "Plains", "Plains", "Plains", "Plains", "Island", "Island", "Island")
        alice.activates(oren)
        playOut()
        expect {
            nameOf(oren) shouldBe "Oren, Wardwalker"
            card(oren).counters(CounterKind.Loyalty) shouldBe 7
        }
        alice.activates(oren, ability = 2)
        playOut()
        alice.activates(oren, ability = 2)
        playOut()
        expect {
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 4
        }
        assertTrue(runCatching { alice.activates(oren, ability = 2) }.isFailure, "a third loyalty ability this turn")
    }

    // CR 602.5d: "Activate only as a sorcery": not in the combat step
    @Test
    fun orenCantMeldOutsideAMainPhase() = scenario {
        libraries()
        alice.battlefield(oren, stones, "Plains", "Plains", "Plains", "Plains", "Island", "Island", "Island")
        advanceTo(TurnStep.BeginningOfCombat)
        assertFailsWith<IllegalArgumentException> { alice.activates(oren) }
    }

    // ---- Entering attacking with several opponents

    // CR 508.4: a token put onto the battlefield attacking attacks the player its controller chooses (Carol), not the one the creature
    // that made it attacks (Bob)
    @Test
    fun aTokenEnteringAttackingAttacksTheChosenOpponent() = scenario(players = 3) {
        libraries()
        alice.battlefield(skirmishHerald)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(skirmishHerald), TargetRef.Player(bob.id)))))
        playOut()
        val target = decision<Decision.Targets>()
        assertEquals(setOf(TargetRef.Player(bob.id), TargetRef.Player(carol.id)), target.options.toSet())
        answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        playOut()
        val token = card("Goblin Token")
        assertEquals(listOf(TargetRef.Player(carol.id)), state.combat!!.attackers.filter { it.id == token }.map { it.target })
    }
}
