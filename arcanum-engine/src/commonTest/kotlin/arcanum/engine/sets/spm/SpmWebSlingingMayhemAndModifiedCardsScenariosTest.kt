package arcanum.engine.sets.spm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import kotlin.test.Test

/**
 * Marvel's Spider-Man cards of parser chunks 01–03 in play (the backfill of 2026-11-03): web-slinging heroes and spells,
 * mayhem cards and modified creatures, each card's own abilities on top of the mechanics [SpmMechanicsScenariosTest]
 * covers. Thrum, Hive Incarnate and Isle of Webbed Folk have only keywords and mayhem, played there.
 */
class SpmWebSlingingMayhemAndModifiedCardsScenariosTest {
    private val tinglingPremonition = Cards.invent("Tingling Premonition", "{1}{U}", "Instant", """
        Web-slinging {U} (You may cast this spell for {U} if you also return a tapped creature you control to its owner's hand.)
        Counter target instant spell, sorcery spell, or triggered ability.
    """)
    private val corinVael = Cards.invent("Corin Vael, Borough Dreamer", "{4}{G}", "Legendary Creature — Spider Human Hero", """
        Web-slinging {2}{G} (You may cast this spell for {2}{G} if you also return a tapped creature you control to its owner's hand.)
        When Corin Vael enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """, "4/3")
    private val lyrra = Cards.invent("Lyrra, Strand Spinner", "{2}{G}{W}", "Legendary Creature — Spider Human Hero", """
        Web-slinging {1}{G}{W} (You may cast this spell for {1}{G}{W} if you also return a tapped creature you control to its owner's hand.)
        Whenever you cast a creature spell, create a 1/1 green and white Human Citizen creature token.
        {3}{G}{W}: Creatures you control get +2/+2 and gain vigilance until end of turn.
    """, "3/5")
    private val monsoonStrider = Cards.invent("Monsoon Strider", "{3}{G}{W}", "Legendary Creature — Spider Human Hero", """
        Web-slinging {1}{G}{W} (You may cast this spell for {1}{G}{W} if you also return a tapped creature you control to its owner's hand.)
        Pavitr's Sevā — Whenever you cast a creature spell, put a +1/+1 counter on target creature you control. It gains flying until end of turn.
    """, "4/4")
    private val tobinAshgrove = Cards.inventFaces("ModalDoubleFaced",
        face("Tobin Ashgrove", "{1}{W}", "Legendary Creature — Human Scientist Hero", """
            When Tobin Ashgrove enters, create a 2/1 green Spider creature token with reach.
            {1}{G}{W}{U}: Transform Tobin Ashgrove. Activate only as a sorcery.
        """, "0/1", null, null),
        face("Astonishing Webcrawler", "{1}{G}{W}{U}", "Legendary Creature — Spider Human Hero", """
            Vigilance, reach
            Each legendary spell you cast that's one or more colors has web-slinging {G}{W}{U}. (You may cast a spell for its web-slinging cost if you also return a tapped creature you control to its owner's hand.)
        """, "4/4", null, null),
    )
    private val cellblockRiot = Cards.invent("Cellblock Riot", "{4}{B}", "Sorcery", """
        Return target creature card from your graveyard to the battlefield with an additional +1/+1 counter on it.
        Mayhem {3}{B} (You may cast this card from your graveyard for {3}{B} if you discarded it this turn. Timing rules still apply.)
    """)
    private val duskWeaver = Cards.invent("Dusk Weaver, Bitter Twin", "{B}{R}", "Legendary Creature — Spider Human Hero", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When Dusk Weaver enters, you may discard a card. If you do, put a +1/+1 counter on him.
        Mayhem {B/R} (You may cast this card from your graveyard for {B/R} if you discarded it this turn. Timing rules still apply.)
    """, "2/1")
    private val paramountGremlinTyrant = Cards.invent("Paramount Gremlin Tyrant", "{1}{B/R}{B/R}", "Legendary Creature — Goblin Villain", """
        At the beginning of your upkeep, discard a card, then create a Treasure token.
        Mayhem {2}{B/R} (You may cast this card from your graveyard for {2}{B/R} if you discarded it this turn. Timing rules still apply.)
    """, "5/4")
    private val argentVixen = Cards.invent("Argent Vixen, Hired Captain", "{2}{W}", "Legendary Creature — Human Mercenary Hero", """
        When Argent Vixen enters, put a +1/+1 counter on another target creature.
        Whenever Argent Vixen attacks, target modified creature you control gains lifelink until end of turn. (Equipment, Auras you control, and counters are modifications.)
    """, "2/3")
    private val teyana = Cards.invent("Teyana, Heart of the Weave", "{1}{R}{W}", "Legendary Creature — Spider Human Hero", """
        Whenever you attack, put a +1/+1 counter on target attacking creature.
        Whenever a modified creature you control deals combat damage to a player, exile the top card of your library. You may play that card this turn. (Equipment, Auras you control, and counters are modifications.)
    """, "3/3")
    private val livingsteelShell = Cards.invent("Livingsteel Shell", "{2}{W}{U}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target creature you control.
        Equipped creature gets +2/+2 and has "Whenever this creature deals combat damage to a player, draw a card for each modified creature you control." (Equipment, Auras you control, and counters are modifications.)
        Equip {2}
    """)
    private val unbendingResolve = Cards.invent("Unbending Resolve", "{1}{G}", "Instant", "Until end of turn, target creature you control gains indestructible and \"Whenever this creature is dealt damage, put that many +1/+1 counters on it.\"")
    private val steelweaveArachnid = Cards.invent("Steelweave Arachnid, Prototype Mk II", "{3}", "Legendary Artifact Creature — Spider Hero", """
        Vigilance
        {T}: Put a +1/+1 counter on each artifact creature and/or Vehicle you control.
        {2}, Remove two +1/+1 counters from among artifacts you control: Draw a card.
    """, "2/3")
    private val darioVance = Cards.inventFaces("ModalDoubleFaced",
        face("Dario Vance", "{1}{G}", "Legendary Creature — Human Citizen Hero", """
            When Dario Vance enters, put a +1/+1 counter on each of up to two target creatures.
            {3}{R}{G}{W}: Transform Dario Vance. Activate only as a sorcery.
        """, "1/2", null, null),
        face("Unrivaled Webstalker", "{3}{R}{G}{W}", "Legendary Creature — Spider Human Hero", """
            First strike, haste
            Camouflage — {2}: Put a +1/+1 counter on Unrivaled Webstalker. He gains hexproof and becomes colorless until end of turn.
            Whenever you attack, double the number of each kind of counter on each Spider and legendary creature you control.
        """, "4/3", null, null),
    )
    private val gizmo = Cards.invent("Rooftop Gizmo", "{2}", "Artifact Creature — Construct", "", "1/1")
    private val bear = spmBear
    private val ogre = spmOgre

    // ---- Chunk 01: web-slinging

    /** Web-slinging for {U}, a tapped creature back to the hand: the opponent's instant is countered. */
    // CR 702.188a, 701.6a
    @Test
    fun tinglingPremonitionCountersAnInstantForWebSlinging() = scenario {
        alice.battlefield("Island")
        alice.battlefield(bear, tapped = true)
        alice.hand(tinglingPremonition)
        bob.battlefield("Mountain")
        bob.hand(Spm.shock)
        turn(active = bob)
        bob.casts(Spm.shock, target = "alice")
        bob.passes()
        alice.casts(tinglingPremonition, target = Spm.shock, way = webSlingingOf(tinglingPremonition), choosing = listOf(bear))
        playOut()
        expect {
            alice.life shouldBe 20
            bob.graveyard has Spm.shock
            alice.hand has bear
        }
    }

    /** A triggered ability on the Stack is countered: the attacker's ping never happens. */
    // CR 701.6b
    @Test
    fun tinglingPremonitionCountersATriggeredAbility() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(tinglingPremonition)
        bob.battlefield(Spm.pinger)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(Spm.pinger)
        bob.passes()
        alice.casts(tinglingPremonition)
        answer(Answer.Targets(listOf(decision<Decision.Targets>().options.first { (it as? TargetRef.Object)?.id is ObjectId.StackItem })))
        playOut()
        expect { alice.life shouldBe 20 }
    }

    /** Cast with web-slinging, Corin Vael puts a basic land onto the battlefield tapped. */
    // CR 701.23a, 305.6
    @Test
    fun corinVaelFetchesATappedBasic() = scenario {
        alice.battlefield("Forest", "Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(corinVael)
        alice.library("Mountain")
        alice.casts(corinVael, way = webSlingingOf(corinVael), choosing = listOf(bear))
        playOut("Mountain")
        expect {
            card("Mountain").zone shouldBe Zone.Battlefield
            card("Mountain").tapped shouldBe true
            corinVael.zone shouldBe Zone.Battlefield
        }
    }

    /** A creature spell makes a Citizen; the activated ability pumps the team and gives vigilance. */
    // CR 603.2, 611.2c
    @Test
    fun lyrraMakesCitizensAndPumpsTheTeam() = scenario {
        alice.battlefield(lyrra, "Forest", "Forest", "Forest", "Forest", "Plains", "Plains", "Plains")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest#1"))
        playOut()
        expect { alice.battlefield has "Human Citizen Token" }
        alice.activates(lyrra)
        playOut()
        expect {
            bear.power shouldBe 4
            "Human Citizen Token".power shouldBe 3
            lyrra.power shouldBe 5
            bear hasKeyword Keyword.Vigilance
        }
    }

    /** Each creature spell puts a +1/+1 counter on a creature, which flies until end of turn. */
    // CR 603.2, 122.1a
    @Test
    fun monsoonStriderCountersAndFlight() = scenario {
        alice.battlefield(monsoonStrider, ogre, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        playOut(ogre)
        expect {
            ogre.counters(p1p1) shouldBe 1
            ogre hasKeyword Keyword.Flying
        }
    }

    /** Tobin makes a Spider as he enters, then transforms into the 4/4 web-slinging granter. */
    // CR 701.27a, 712.8
    @Test
    fun tobinAshgroveMakesASpiderAndTransforms() = scenario {
        alice.battlefield("Plains", "Plains", "Forest", "Plains", "Island", "Island")
        alice.hand(tobinAshgrove)
        alice.casts(tobinAshgrove, paying = listOf("Plains", "Plains#1"))
        playOut()
        expect { alice.battlefield has "Spider Token" }
        advanceTo(TurnStep.PostcombatMain)
        alice.activates("Tobin Ashgrove")
        playOut()
        expect {
            "Tobin Ashgrove".view.name shouldBe "Astonishing Webcrawler"
            "Tobin Ashgrove".power shouldBe 4
        }
    }

    // ---- Chunk 02: mayhem

    /** The creature card comes back with an extra +1/+1 counter. */
    // CR 122.6
    @Test
    fun cellblockRiotReturnsWithACounter() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(ogre)
        alice.hand(cellblockRiot)
        alice.casts(cellblockRiot, target = ogre)
        playOut()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.counters(p1p1) shouldBe 1
        }
    }

    /** Discarding as he enters gives him a counter; the discarded card can then be cast with its mayhem. */
    // CR 603.12, 702.187a
    @Test
    fun duskWeaverDiscardsForACounter() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(duskWeaver, bear)
        alice.casts(duskWeaver)
        playOut(bear)
        expect {
            duskWeaver.counters(p1p1) shouldBe 1
            alice.graveyard has bear
        }
    }

    /** Each upkeep: a discard, then a Treasure. */
    // CR 503.1a, 701.9a
    @Test
    fun paramountGremlinTyrantDiscardsForATreasure() = scenario {
        alice.battlefield(paramountGremlinTyrant)
        alice.hand(bear)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(bear)
        expect {
            alice.graveyard has bear
            alice.battlefield has "Treasure Token"
        }
    }

    // ---- Chunk 03: modified creatures

    /** A counter on another creature as she enters. */
    // CR 115.1
    @Test
    fun argentVixenPutsACounterOnAnother() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(argentVixen)
        alice.casts(argentVixen)
        playOut(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Attacking, she gives a modified creature lifelink. */
    // CR 700.9, 702.15a
    @Test
    fun argentVixenGivesAModifiedCreatureLifelink() = scenario {
        alice.battlefield(argentVixen, ogre)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(argentVixen)
        playOut(bear)
        expect { bear hasKeyword Keyword.Lifelink }
        expect { ogre lacksKeyword Keyword.Lifelink }
    }

    /** Attacking puts a counter on the attacker; the modified attacker's damage exiles the top card, which is played. */
    // CR 700.9, 611.2a
    @Test
    fun teyanaCountersAndPlaysTheTopCard() = scenario {
        alice.battlefield(teyana, bear)
        alice.library("Forest", ogre)
        hits(bear, picks = listOf(bear))
        expect {
            bear.counters(p1p1) shouldBe 1
            bob.life shouldBe 17
            card("Forest").zone shouldBe Zone.Exile
        }
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    /** Attached as it enters: +2/+2, and the hit draws a card for each modified creature. */
    // CR 301.5, 700.9
    @Test
    fun livingsteelShellDrawsForModifiedCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", bear)
        alice.battlefield(ogre, counters = listOf(p1p1 to 1))
        alice.hand(livingsteelShell)
        alice.library(spmGiant, spmGiant, spmGiant)
        alice.casts(livingsteelShell)
        playOut(bear)
        expect { bear.power shouldBe 4 }
        hits(bear)
        expect {
            bob.life shouldBe 16
            alice.hand.size shouldBe 2
        }
    }

    /** Indestructible, and 2 damage becomes two +1/+1 counters. */
    // CR 702.12b, 120.3
    @Test
    fun unbendingResolveTurnsDamageIntoCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain", bear)
        alice.hand(unbendingResolve, Spm.shock)
        alice.casts(unbendingResolve, target = bear)
        playOut()
        alice.casts(Spm.shock, target = bear)
        playOut()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 2
        }
    }

    /** A counter on each artifact creature; two counters from among artifacts pay for a card. */
    // CR 122.1, 602.2
    @Test
    fun steelweaveArachnidCountsAndDraws() = scenario {
        alice.battlefield(steelweaveArachnid, gizmo, bear, "Plains", "Plains")
        alice.library(ogre)
        alice.activates(steelweaveArachnid)
        playOut()
        expect {
            steelweaveArachnid.counters(p1p1) shouldBe 1
            gizmo.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
        alice.activates(steelweaveArachnid, ability = 1, choosing = listOf(steelweaveArachnid, gizmo))
        playOut()
        expect {
            alice.hand has ogre
            steelweaveArachnid.counters(p1p1) shouldBe 0
            gizmo.counters(p1p1) shouldBe 0
        }
    }

    /** Counters on up to two creatures as he enters; the transformed Webstalker keeps them doubling. */
    // CR 115.1, 701.27a
    @Test
    fun darioVanceCountersTwoCreatures() = scenario {
        alice.battlefield("Forest", "Forest", bear, ogre)
        alice.hand(darioVance)
        alice.casts(darioVance)
        playOut(bear, ogre)
        expect {
            bear.counters(p1p1) shouldBe 1
            ogre.counters(p1p1) shouldBe 1
            "Dario Vance".types.contains(CardType.Creature) shouldBe true
        }
    }
}
