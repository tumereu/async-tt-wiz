package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Star Trek chunk 17 in play, three players: sunburst with a destroy by counters, a card that exiles itself from the graveyard, abilities that
 * artifact creatures gain, a static flying during your turn, a tap trigger with a this-turn condition, a Saga that hits each player, a fight. */
class TrkChunk17DestroyDiscardDrawFightGainScenariosTest {
    private val rig = Cards.invent("Trk17 Tuned Detonation Rig", "{X}", "Artifact", """
        Sunburst (This artifact enters with a charge counter on it for each color of mana spent to cast it.)
        {2}, Sacrifice this artifact: Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact.
    """.trimIndent())
    private val colossus = Cards.invent("Trk17 Crystal Colossus Of Glass", "{8}", "Artifact Creature — Elemental", """
        Flying
        When this creature enters, if you cast it, destroy all nonartifact creatures.
    """.trimIndent(), "7/7")
    private val armus = Cards.invent("Trk17 Armus Hide Of Ruin", "{5}{B}{B}", "Legendary Creature — Ooze Horror", """
        Menace
        At the beginning of your end step, target opponent discards two cards.
        {2}{B}, Discard this card: Target creature gets -4/-4 until end of turn.
    """.trimIndent(), "6/6")
    private val trimatrix = Cards.invent("Trk17 Collation Tri-Array", "{B}", "Artifact Creature — Borg", "Tap three untapped artifact creatures you control: You draw a card and lose 1 life.", "1/1")
    private val husk = Cards.invent("Trk17 Unyielding Drone Husk", "{B}", "Artifact Creature — Robot", """
        Lifelink
        {2}{B}, Exile this card from your graveyard: You draw a card and lose 1 life.
    """.trimIndent(), "1/1")
    private val sulu = Cards.invent("Trk17 Sulu Skyward Duelist", "{1}{W}", "Legendary Creature — Human Pilot", """
        First strike
        Whenever Trk17 Sulu Skyward Duelist becomes tapped, if an Equipment or Spacecraft entered the battlefield under your control this turn, draw a card.
    """.trimIndent(), "2/2")
    private val blade = Cards.invent("Trk17 Plain Blade", "{1}", "Artifact — Equipment", "")
    private val flashBlade = Cards.invent("Trk17 Quick Blade", "{1}", "Artifact — Equipment", "Flash")
    private val ship = Cards.invent("Trk17 Plain Ship", "{1}", "Artifact — Spacecraft", "")
    private val ace = Cards.invent("Trk17 Shuttle Wing Captain", "{4}{W}", "Creature — Kelpien Pilot", """
        During your turn, this creature has flying.
        Whenever this creature becomes tapped, you gain 2 life.
    """.trimIndent(), "4/4")
    private val underling = Cards.invent("Trk17 Command Underling", "{W}", "Artifact Creature — Borg", "Artifact creatures you control have vigilance.", "2/1")
    private val clank = Cards.invent("Trk17 Clank", "{2}", "Artifact Creature — Construct", "", "2/2")
    private val yearOfEmbers = Cards.invent("Trk17 Year Of Embers", "{1}{U}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I, II, III — Each player draws a card. This Saga deals 2 damage to each player.
    """.trimIndent())
    private val bloodFever = Cards.invent("Trk17 Blood Fever Hour", "{2}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Target creature you control fights up to one target creature an opponent controls.
        II — You gain 3 life.
        III — Return target creature card from your graveyard to your hand.
    """.trimIndent())
    private val drifter = Cards.invent("Trk17 Drifter", "{1}", "Creature — Human", "", "1/1")
    private val bear = Cards.invent("Trk17 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Trk17 Ogre", "{2}{G}", "Creature — Ogre", "", "3/3")

    // CR 702.44a (sunburst: two colours spent, two counters), 701.8, 608.2h (the sacrificed rig's counters as it last existed):
    // exactly the nonland permanents of mana value 2 die, whoever controls them
    @Test
    fun rigDestroysEveryNonlandPermanentOfItsChargeValue() = scenario(players = 3) {
        alice.battlefield("Forest", "Mountain", "Island", "Island", bear)
        alice.hand(rig)
        bob.battlefield(bear, drifter, "Swamp")
        carol.battlefield(ogre, "Plains")
        alice.casts(rig, x = 2, paying = listOf("Forest", "Mountain"))
        resolveAll()
        expect { rig.counters(CounterKind.Named("charge")) shouldBe 2 }
        alice.activates(rig, paying = listOf("Island", "Island#1"))
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has bear
            bob.battlefield has drifter
            bob.battlefield has "Swamp"
            carol.battlefield has ogre
            carol.battlefield has "Plains"
            rig.zone shouldBe Zone.Graveyard
        }
    }

    // CR 603.4 (cast it), 701.8: all nonartifact creatures die, artifact creatures (also the other players') stay
    @Test
    fun colossusDestroysNonartifactCreaturesWhenCast() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", ogre)
        alice.hand(colossus)
        bob.battlefield(bear, clank)
        carol.battlefield(drifter)
        alice.casts(colossus)
        resolveAll()
        expect {
            colossus.zone shouldBe Zone.Battlefield
            clank.zone shouldBe Zone.Battlefield
            alice.graveyard has ogre
            bob.graveyard has bear
            carol.graveyard has drifter
        }
    }

    // CR 603.4, 701.9: only the chosen opponent discards, two cards
    @Test
    fun armusMakesTheChosenOpponentDiscardTwo() = scenario(players = 3) {
        alice.battlefield(armus)
        bob.hand("Forest", "Forest")
        carol.hand("Swamp", "Island")
        alice.library("Mountain", "Mountain", "Mountain")
        advanceTo(TurnStep.End)
        resolveAll()
        answer(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        expect {
            carol.hand.size shouldBe 0
            carol.graveyard.names.size shouldBe 2
            bob.hand.size shouldBe 2
        }
    }

    // CR 113.6b/m, 602.2: discarding itself as a cost from the hand
    @Test
    fun armusDiscardedFromHandShrinksACreature() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(armus)
        bob.battlefield(ogre)
        alice.activates(armus, target = ogre)
        resolveAll()
        expect {
            armus.zone shouldBe Zone.Graveyard
            bob.graveyard has ogre
        }
    }

    // CR 602.2, 602.2b: the three tapped objects are artifact creatures you control; another player's don't count (the audit's missing "you" is implicit)
    @Test
    fun trimatrixCannotUseAnotherPlayersArtifactCreatures() = scenario(players = 3) {
        alice.battlefield(trimatrix, clank)
        bob.battlefield(clank)
        alice.library("Forest", "Forest")
        assertFailsWith<Throwable> { alice.activates(trimatrix) }
    }

    @Test
    fun trimatrixTapsThreeOfYourOwnToDraw() = scenario(players = 3) {
        alice.battlefield(trimatrix, clank, clank)
        bob.battlefield(clank)
        alice.library("Forest", "Forest")
        alice.activates(trimatrix)
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
            trimatrix.tapped shouldBe true
            bob.life shouldBe 20
        }
    }

    // CR 113.6m, 602.2: from the graveyard, paying {2}{B}, it exiles itself, you draw and lose 1 life
    @Test
    fun huskExilesItselfFromTheGraveyardToDraw() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(husk)
        alice.library("Forest", "Forest")
        alice.activates(husk)
        resolveAll()
        expect {
            husk.zone shouldBe Zone.Exile
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
        }
    }

    // CR 613.1f, 702.20 (vigilance): your artifact creatures don't tap to attack; a nonartifact creature does; the other players' creatures don't have it
    @Test
    fun underlingGivesYourArtifactCreaturesVigilance() = scenario(players = 3) {
        alice.battlefield(underling, clank, ogre)
        bob.battlefield(Cards.invent("Trk17 Bob Clank", "{2}", "Artifact Creature — Construct", "", "2/2"))
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(clank, defender = "bob")
        expect {
            clank hasKeyword Keyword.Vigilance.kind
            underling hasKeyword Keyword.Vigilance.kind
            clank.tapped shouldBe false
        }
        expect { card("Trk17 Bob Clank") lacksKeyword Keyword.Vigilance.kind }
        expect { ogre lacksKeyword Keyword.Vigilance.kind }
    }

    // CR 611.3: flying only during your turn; CR 701.26 attacking taps it: you gain 2 life
    @Test
    fun aceFliesOnlyOnItsControllersTurn() = scenario(players = 3) {
        alice.battlefield(ace)
        turn(step = TurnStep.PrecombatMain)
        expect { ace hasKeyword Keyword.Flying.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ace, defender = "bob")
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun aceDoesNotFlyOnAnotherPlayersTurn() = scenario(players = 3) {
        alice.battlefield(ace)
        turn(number = 2, active = bob)
        expect { ace lacksKeyword Keyword.Flying.kind }
    }

    private fun Scenario.suluAttacks() {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sulu, defender = "bob")
        resolveAll()
    }

    // CR 603.4: nothing entered, no card
    @Test
    fun suluDrawsNothingWithoutAnEquipmentOrSpacecraft() = scenario(players = 3) {
        alice.battlefield(sulu)
        alice.library("Forest", "Forest")
        turn(step = TurnStep.PrecombatMain)
        suluAttacks()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 603.4, 701.26: an Equipment or a Spacecraft you put onto the battlefield this turn turns tapping into a draw
    @Test
    fun suluDrawsAfterYourEquipmentOrSpacecraftEntered() {
        for (permanent in listOf(blade, ship)) scenario(players = 3) {
            alice.battlefield(sulu, "Forest")
            alice.hand(permanent)
            alice.library("Forest", "Forest")
            turn(step = TurnStep.PrecombatMain)
            alice.casts(permanent)
            resolveAll()
            suluAttacks()
            expect { alice.hand.size shouldBe 1 }
        }
    }

    // CR 603.4 (audit: "under your control"): an Equipment that entered under another player's control doesn't count
    @Test
    fun suluIgnoresAnEquipmentThatEnteredUnderAnotherPlayersControl() = scenario(players = 3) {
        alice.battlefield(sulu)
        bob.battlefield("Forest")
        bob.hand(flashBlade)
        alice.library("Forest", "Forest")
        turn(step = TurnStep.PrecombatMain)
        alice.passes()
        bob.casts(flashBlade)
        resolveAll()
        expect { flashBlade.zone shouldBe Zone.Battlefield }
        suluAttacks()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 714.2, 120.3, 121.1: chapter I hits every player: each draws and takes 2
    @Test
    fun yearOfEmbersDrawsAndDamagesEachPlayer() = scenario(players = 3) {
        alice.battlefield("Island", "Mountain", "Forest")
        alice.hand(yearOfEmbers)
        for (p in listOf(alice, bob, carol)) p.library("Plains", "Plains")
        alice.casts(yearOfEmbers)
        resolveAll()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.hand.size shouldBe 1
            bob.hand.size shouldBe 1
            carol.hand.size shouldBe 1
        }
    }

    // CR 701.14a (fight), 115.6 ("up to one" opponent's creature), 120.3
    @Test
    fun bloodFeverFightsAChosenOpponentCreature() = scenario(players = 3) {
        alice.battlefield(ogre)
        bob.battlefield(bear)
        carol.battlefield(drifter)
        alice.hand(bloodFever)
        alice.battlefield("Forest", "Forest", "Forest")
        alice.casts(bloodFever)
        resolveAll()
        answer(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect {
            bob.graveyard has bear
            carol.battlefield has drifter
            ogre.view.obj?.damage shouldBe 2
        }
    }

    // CR 701.14b, 115.6: with no opponent's creature to choose, the ability still has its first target and nothing is dealt damage
    @Test
    fun bloodFeverWithoutAnOpponentCreatureDoesNothing() = scenario(players = 3) {
        alice.battlefield(ogre, "Forest", "Forest", "Forest")
        carol.battlefield("Plains")
        alice.hand(bloodFever)
        alice.casts(bloodFever)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.view.obj?.damage shouldBe 0
        }
    }
}
