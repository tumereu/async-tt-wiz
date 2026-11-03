package arcanum.engine.sets.dsk

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dsk.Dsk.bear
import arcanum.engine.sets.dsk.Dsk.charm
import arcanum.engine.sets.dsk.Dsk.giant
import arcanum.engine.sets.dsk.Dsk.human
import arcanum.engine.sets.dsk.Dsk.ogre
import arcanum.engine.sets.dsk.Dsk.p1p1
import arcanum.engine.sets.dsk.Dsk.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Duskmourn's other red, green, multicolored, artifact and land cards in play (parser chunks 11–14). */
class DskRedGreenMulticolorAndArtifactCardsScenariosTest {
    private val traitorsBargain = Cards.invent("Traitor's Bargain", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature or enchantment or pay {2}.
        Traitor's Bargain deals 5 damage to target creature. If that creature would die this turn, exile it instead.
    """)
    private val buzzsaw = Cards.invent("Buzzsaw", "{1}{R}", "Artifact — Equipment", """
        When this Equipment enters, it deals 3 damage to up to one target creature.
        Whenever one or more creatures die, put a rev counter on this Equipment.
        Equipped creature gets +X/+0, where X is the number of rev counters on this Equipment.
        Equip {3}
    """)
    private val hexedRecording = Cards.invent("Hexed Recording", "{2}{R}{R}", "Artifact", """
        Whenever you cast an instant or sorcery spell, put a time counter on this artifact. Then if there are seven or more time counters on it, remove those counters and it deals 20 damage to you.
        {T}: When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.
    """)
    private val dreadOfBeingHunted = Cards.invent("Dread of Being Hunted", "{1}{R}{R}", "Enchantment Creature — Nightmare", """
        Haste
        This creature must be blocked if able.
    """, "4/2")
    private val snatchThePrize = Cards.invent("Snatch the Prize", "{1}{R}", "Sorcery", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards. If the discarded card wasn't a land card, Snatch the Prize deals 2 damage to each opponent.
    """)
    private val nolanSwiftEscapist = Cards.invent("Nolan, Swift Escapist", "{R}", "Legendary Creature — Human Coward", """
        Nolan can't block.
        Whenever a creature you control becomes blocked, you may exile it. You may play that card from exile this turn.
    """, "2/1")
    private val tatteredPlaymate = Cards.invent("Tattered Playmate", "{1}{R}", "Artifact Creature — Toy", "{1}, {T}: Target creature with power 2 or less can't be blocked this turn.", "2/2")
    private val razorkinPinhead = Cards.invent("Razorkin Pinhead", "{R}{R}", "Creature — Human Assassin", """
        This creature has first strike during your turn.
        Whenever an opponent draws a card, this creature deals 1 damage to them.
    """, "2/2")
    private val searingDragonfire = Cards.invent("Searing Dragonfire", "{1}{R}", "Instant", "Searing Dragonfire deals 3 damage to target creature or planeswalker. If that creature or planeswalker would die this turn, exile it instead.")
    private val shriekingNemesis = Cards.invent("Shrieking Nemesis", "{2}{R}", "Creature — Spirit", """
        Haste
        Whenever this creature is dealt damage, it deals that much damage to any other target. If a player is dealt damage this way, they can't gain life for the rest of the game.
    """, "3/3")
    private val ordealOfAgony = Cards.invent("Ordeal of Agony", "{R}", "Sorcery", "Choose two target creatures controlled by the same opponent. That player chooses one of those creatures. Ordeal of Agony deals 5 damage to that creature, and the other can't block this turn.")
    private val untimelyBreakdown = Cards.invent("Untimely Breakdown", "{1}{R}", "Instant", """
        Choose one —
        • Destroy target artifact.
        • Change the target of target spell or ability with a single target.
        • One or two target creatures can't block this turn.
    """)
    private val vengefulSeizure = Cards.invent("Vengeful Seizure", "{2}{R}", "Sorcery", "Gain control of target creature until end of turn. Untap it. It gains haste until end of turn. You may discard a card. If you do, draw a card.")
    private val waltzOfFury = Cards.invent("Waltz of Fury", "{3}{R}{R}", "Sorcery", "Target creature you control deals damage equal to its power to each other creature. Until end of turn, whenever a creature you control dies, exile the top card of your library. You may play it until the end of your next turn.")
    private val altanorTheThriceSummoned = Cards.invent("Altanor, the Thrice-Summoned", "{5}{G}{G}", "Legendary Creature — Insect Beast", """
        Trample
        Whenever Altanor becomes the target of a spell or ability an opponent controls, draw a card.
        {1}{G}, Discard this card: Return target land card from your graveyard to the battlefield tapped.
    """, "9/9")
    private val anthrocrawler = Cards.invent("Anthrocrawler", "{3}{G}", "Creature — Insect", """
        Reach
        When this creature enters, you may discard a card or pay {2}. When you do, destroy target Room.
    """, "3/4")
    private val freeingParting = Cards.invent("Freeing Parting", "{1}{G}", "Sorcery", "The owner of target artifact or enchantment an opponent controls shuffles it into their library. You may shuffle up to four target cards from your graveyard into your library.")
    private val concertedClobbering = Cards.invent("Concerted Clobbering", "{G}", "Sorcery", "Tap one or two target untapped creatures you control. They each deal damage equal to their power to target creature an opponent controls.")
    private val fleshBorer = Cards.invent("Flesh Borer", "{1}{G}", "Creature — Insect", """
        Deathtouch
        Whenever this creature attacks, another target creature you control gains deathtouch until end of turn.
    """, "2/2")
    private val hedgeMulcher = Cards.invent("Hedge Mulcher", "{2}{G}{G}", "Artifact — Vehicle", """
        Whenever this Vehicle attacks, you may mill two cards.
        Whenever one or more land cards are put into your graveyard from your library, put them onto the battlefield tapped.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "5/5")
    private val insidiousMold = Cards.invent("Insidious Mold", "{G}", "Creature — Fungus", """
        {2}, Sacrifice this creature: Choose one —
        • Destroy target artifact.
        • Destroy target enchantment.
        • Draw a card. Then you may put a land card from your hand onto the battlefield tapped.
    """, "1/2")
    private val monstrousArrival = Cards.invent("Monstrous Arrival", "{1}{G}", "Sorcery", """
        As an additional cost to cast this spell, choose a creature you control or reveal a creature card from your hand.
        Monstrous Arrival deals damage equal to the power of the creature you chose or the card you revealed to target creature.
    """)
    private val utterItsName = Cards.invent("Utter Its Name", "{1}{G}", "Sorcery", """
        Mill three cards. Then you may return a creature or land card from your graveyard to your hand.
        Exile this card and two other cards named Utter Its Name from your graveyard: Search your graveyard, hand, and/or library for a card named Altanor, the Thrice-Summoned and put it onto the battlefield. If you search your library this way, shuffle. Activate only as a sorcery.
    """)
    private val twitchyDoll = Cards.invent("Twitchy Doll", "{1}{G}", "Artifact Creature — Spider Toy", """
        {T}: Add one mana of any color. Put a nest counter on this creature.
        {T}, Sacrifice this creature: Create a 2/2 green Spider creature token with reach for each counter on this creature. Activate only as a sorcery.
    """, "2/2")
    private val tavikThePummeler = Cards.invent("Tavik, the Pummeler", "{1}{G}{G}", "Legendary Creature — Elf Warrior", """
        Tap another untapped creature you control: Tavik gains indestructible until end of turn. Tap it.
        {3}{G}{G}: Creatures you control get +X/+X until end of turn, where X is the greatest power among creatures you control.
    """, "3/3")
    private val annabelForsakenDoll = Cards.invent("Annabel, Forsaken Doll", "{R}{W}", "Legendary Artifact Creature — Toy", "Whenever Annabel attacks, it deals X damage to each opponent and you gain X life, where X is the number of creatures you control with power 2 or less.", "1/3")
    private val cricketBat = Cards.invent("Cricket Bat", "{G}{W}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target creature you control.
        Equipped creature gets +1/+1.
        Whenever equipped creature attacks, tap up to one target creature.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val theMerryBalloonMan = Cards.invent("The Merry Balloon Man", "{1}{R}{W}", "Legendary Creature — Human Clown", """
        Haste
        {1}, {T}: Create a token that's a copy of another target creature you control, except it's a 1/1 red Balloon creature in addition to its other colors and types and it has flying and haste. Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
    """, "1/4")
    private val kaidenBaneOfDreams = Cards.invent("Kaiden, Bane of Dreams", "{2}{U}{B}", "Legendary Planeswalker — Kaito", """
        Ninjutsu {1}{U}{B} ({1}{U}{B}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)
        During your turn, as long as Kaiden has one or more loyalty counters on him, he's a 3/4 Ninja creature and has hexproof.
        [+1]: You get an emblem with "Ninjas you control get +1/+1."
        [0]: Surveil 2. Then draw a card for each opponent who lost life this turn.
        [−2]: Tap target creature. Put two stun counters on it.
    """, loyalty = "4")
    private val midnightMischief = Cards.invent("Midnight Mischief", "{2}{R}{W}", "Sorcery", "Create three 1/1 red Gremlin creature tokens. Gremlins you control gain menace, lifelink, and haste until end of turn. (A creature with menace can't be blocked except by two or more creatures.)")
    private val nashkoSeekerInTheDark = Cards.invent("Nashko, Seeker in the Dark", "{U}{B}", "Legendary Creature — Rat Ninja Wizard", """
        Menace
        Whenever Nashko deals combat damage to a player, you mill that many cards. You may put any number of legendary and/or enchantment cards from among them into your hand. If you put no cards into your hand this way, put a +1/+1 counter on Nashko.
    """, "2/2")
    private val nikaLightOfPromise = Cards.invent("Nika, Light of Promise", "{2}{W}{U}", "Legendary Creature — Human Wizard", """
        When Nika enters, create two Shard tokens. (They're enchantments with "{2}, Sacrifice this token: Scry 1, then draw a card.")
        {2}, {T}: Exile target nonlegendary creature you control. Shards you control become copies of it until the next end step. Return it to the battlefield under its owner's control at the beginning of the next end step.
    """, "3/4")
    private val riteOfTheLanternMoth = Cards.invent("Rite of the Lantern Moth", "{1}{W}{B}{B}", "Sorcery", """
        Return target creature card from your graveyard to the battlefield with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        Flashback {3}{W}{W}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val sawtoothSkinflayer = Cards.invent("Sawtooth Skinflayer", "{1}{B}{R}", "Creature — Human Assassin", """
        Menace
        {2}, Sacrifice another creature or enchantment: Put a +1/+1 counter on this creature.
        At the beginning of your end step, if you sacrificed one or more permanents this turn, this creature deals that much damage to any target.
    """, "3/2")
    private val undeadDasher = Cards.invent("Undead Dasher", "{B}{R}", "Creature — Zombie", """
        Trample, haste
        You may cast this card from your graveyard if a non-Zombie creature died this turn. If you do, this creature enters with a +1/+1 counter on it.
    """, "2/2")
    private val zimraAllWondering = Cards.invent("Zimra, All-Wondering", "{1}{G}{U}", "Legendary Creature — Human Wizard", """
        At the beginning of your end step, if a land entered the battlefield under your control this turn and you control a prime number of lands, create Prima, the Undivided, a legendary 0/0 green and blue Fractal creature token, then put that many +1/+1 counters on it. (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, and 31 are prime numbers.)
    """, "1/1")
    private val pounceInTheBox = Cards.invent("Pounce-in-the-Box", "{3}", "Artifact Creature — Toy", "Whenever this creature attacks, you may have it get +4/+0 until end of turn. If you do, sacrifice it at the beginning of the next end step.", "2/4")
    private val spiritVacuum = Cards.invent("Spirit Vacuum", "{1}", "Artifact", """
        {T}: Exile target card from a graveyard.
        {6}, {T}, Sacrifice this artifact: Put each creature card exiled with this artifact onto the battlefield under your control with a flying counter on it. Each of them is a 1/1 Spirit in addition to its other types. Activate only as a sorcery.
    """)
    private val hauntedFoldingScreen = Cards.invent("Haunted Folding Screen", "{3}", "Artifact", """
        {T}: Add {W} or {B}.
        {T}, Pay 1 life: Add {G}, {U}, or {R}.
        {7}: Put seven +1/+1 counters on this artifact. It becomes a 0/0 Spirit creature in addition to its other types. Activate only once.
    """)
    private val maliciousChandelier = Cards.invent("Malicious Chandelier", "{6}", "Artifact Creature — Construct", """
        Flying
        {2}: Put target card from a graveyard on the bottom of its owner's library. Activate only as a sorcery.
    """, "4/4")
    private val morvinMimickingMenace = Cards.invent("Morvin, Mimicking Menace", "{2}", "Legendary Artifact Creature — Toy", "Morvin has all activated abilities of creatures you control that don't have the same name as this creature.", "2/2")
    private val hacksaw = Cards.invent("Hacksaw", "{2}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        Whenever equipped creature attacks, you may sacrifice a permanent other than that creature or this Equipment. If you do, draw a card.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val forsakenCampsite = Cards.invent("Forsaken Campsite", null, "Land", """
        This land enters tapped unless a player has 13 or less life.
        {T}: Add {W} or {U}.
    """)
    private val vulgarothsDen = Cards.invent("Vulgaroth's Den", null, "Enchantment Land", """
        Hexproof
        This land enters tapped. As it enters, choose a color.
        {T}: Add one mana of the chosen color.
    """)
    private val foyer = Cards.inventFaces("Split",
        face("Dim Foyer", "{W}", "Enchantment — Room", "When you unlock this door, you gain 1 life.", null, null, null),
        face("Dusty Pantry", "{1}{W}", "Enchantment — Room", "When you unlock this door, you gain 2 life.", null, null, null),
    )

    // ---- Chunk 11: red

    // CR 601.2b, 601.2f: an additional cost chosen among two; CR 614.1a: exiled instead of dying
    @Test
    fun traitorsBargainSacrificesAndExiles() = scenario {
        alice.battlefield("Mountain", "Mountain", charm)
        alice.hand(traitorsBargain)
        bob.battlefield(ogre)
        alice.casts(traitorsBargain, target = ogre)
        if (pending is Decision.ChooseOption) option("Sacrifice")
        if (pending is Decision.ChooseObjects) alice.chooses(charm)
        payIfAsked()
        resolveAll()
        expect {
            bob.exile has ogre
            alice.graveyard has charm
        }
    }

    @Test
    fun buzzsawBurnsAndRevs() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(buzzsaw)
        bob.battlefield(ogre)
        alice.casts(buzzsaw, paying = listOf("Mountain", "Mountain#1"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            buzzsaw.counters(CounterKind.Named("rev")) shouldBe 1
        }
        alice.activates(buzzsaw, target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    // CR 603.7: "when you next cast an instant or sorcery spell this turn"
    @Test
    fun hexedRecordingCopiesTheNextSpell() = scenario {
        alice.battlefield(hexedRecording, "Mountain")
        alice.hand(shock)
        alice.activates(hexedRecording)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect {
            bob.life shouldBe 16
            hexedRecording.counters(CounterKind.Time) shouldBe 1
        }
    }

    // CR 509.1c: a requirement to block
    @Test
    fun dreadOfBeingHuntedMustBeBlocked() = scenario {
        alice.battlefield(dreadOfBeingHunted)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dreadOfBeingHunted)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks() }
        bob.blocks(bear to dreadOfBeingHunted)
    }

    @Test
    fun snatchThePrizeBurnsForANonland() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(snatchThePrize, ogre)
        alice.library(bear, giant)
        alice.casts(snatchThePrize, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, giant)
            bob.life shouldBe 18
        }
    }

    @Test
    fun nolanSavesABlockedCreature() = scenario {
        alice.battlefield(nolanSwiftEscapist, bear, "Forest", "Forest")
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(giant to bear)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun tatteredPlaymateMakesASmallCreatureUnblockable() = scenario {
        alice.battlefield(tatteredPlaymate, bear, "Mountain")
        bob.battlefield(giant)
        alice.activates(tatteredPlaymate, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun razorkinPinheadPingsOnDraws() = scenario {
        alice.battlefield(razorkinPinhead)
        bob.library(ogre)
        expect { razorkinPinhead hasKeyword Keyword.FirstStrike }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        resolveAll()
        expect {
            bob.life shouldBe 19
            razorkinPinhead lacksKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun searingDragonfireExiles() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(searingDragonfire)
        bob.battlefield(ogre)
        alice.casts(searingDragonfire, target = ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    // CR 119.7: can't gain life for the rest of the game
    @Test
    fun shriekingNemesisPassesDamageOn() = scenario {
        alice.battlefield("Mountain", shriekingNemesis)
        alice.hand(shock)
        bob.battlefield("Plains")
        bob.hand(Dsk.heal)
        alice.casts(shock, target = shriekingNemesis)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
        alice.passes()
        bob.casts(Dsk.heal)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun ordealOfAgonyLetsThemChoose() = scenario {
        alice.battlefield("Mountain")
        alice.hand(ordealOfAgony)
        bob.battlefield(giant, bear)
        alice.casts(ordealOfAgony, targets = listOf(giant, bear))
        resolveAll()
        bob.chooses(giant)
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.battlefield has bear
        }
    }

    @Test
    fun untimelyBreakdownStopsBlockers() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(untimelyBreakdown)
        bob.battlefield(giant)
        alice.casts(untimelyBreakdown, modes = listOf(2), target = giant)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun untimelyBreakdownDestroysAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(untimelyBreakdown)
        bob.battlefield(Dsk.idol)
        alice.casts(untimelyBreakdown, modes = listOf(0), target = Dsk.idol)
        resolveAll()
        expect { bob.graveyard has Dsk.idol }
    }

    @Test
    fun vengefulSeizureStealsAndRummages() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(vengefulSeizure, giant)
        alice.library(bear)
        bob.battlefield(ogre, tapped = true)
        alice.casts(vengefulSeizure, target = ogre)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe false
            alice.hand has bear
        }
    }

    @Test
    fun waltzOfFuryHitsEveryOtherCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", giant, human)
        alice.hand(waltzOfFury)
        alice.library("Forest")
        bob.battlefield(ogre)
        alice.casts(waltzOfFury, target = giant)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has human
            alice.exile has "Forest"
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // ---- Chunk 12: green

    @Test
    fun altanorDrawsWhenTargetedAndReturnsALand() = scenario {
        alice.battlefield(altanorTheThriceSummoned)
        alice.library(bear)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = altanorTheThriceSummoned)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun altanorDiscardsToReturnALand() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(altanorTheThriceSummoned)
        alice.graveyard("Plains")
        alice.activates(altanorTheThriceSummoned, target = "Plains")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            alice.graveyard has altanorTheThriceSummoned
        }
    }

    @Test
    fun anthrocrawlerDestroysARoom() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(anthrocrawler, ogre)
        bob.battlefield(foyer)
        alice.casts(anthrocrawler)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseOption) option("Discard")
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        targetsIfAsked(foyer)
        resolveAll()
        expect { bob.graveyard has foyer }
    }

    @Test
    fun freeingPartingShufflesAwayAndRecycles() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(freeingParting)
        alice.graveyard(bear, ogre)
        bob.battlefield(Dsk.charm)
        alice.casts(freeingParting, targets = listOf(Dsk.charm, bear, ogre))
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect {
            bob.library has Dsk.charm
            alice.library has bear
            alice.library has ogre
        }
    }

    @Test
    fun concertedClobberingGangsUp() = scenario {
        alice.battlefield("Forest", bear, human)
        alice.hand(concertedClobbering)
        bob.battlefield(ogre)
        alice.casts(concertedClobbering, targets = listOf(bear, human, ogre))
        resolveAll()
        expect {
            bob.graveyard has ogre
            bear.tapped shouldBe true
        }
    }

    @Test
    fun fleshBorerSharesDeathtouch() = scenario {
        alice.battlefield(fleshBorer, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(fleshBorer)
        targetsIfAsked(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Deathtouch }
    }

    // CR 702.122: crew; milled lands are put onto the battlefield
    @Test
    fun hedgeMulcherMillsLandsOntoTheBattlefield() = scenario {
        alice.battlefield(hedgeMulcher, bear)
        alice.library("Plains", ogre, giant)
        alice.activates(hedgeMulcher, choosing = listOf(bear))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hedgeMulcher)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
            alice.graveyard has ogre
        }
    }

    @Test
    fun insidiousMoldDrawsAndPlaysALand() = scenario {
        alice.battlefield(insidiousMold, "Forest", "Forest")
        alice.hand("Plains")
        alice.library(bear)
        alice.activates(insidiousMold, modes = listOf(2))
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses("Plains")
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield has "Plains"
        }
    }

    @Test
    fun monstrousArrivalHitsWithTheChosenPower() = scenario {
        alice.battlefield("Forest", "Forest", giant)
        alice.hand(monstrousArrival)
        bob.battlefield(ogre)
        alice.casts(monstrousArrival, target = ogre)
        if (pending is Decision.ChooseOption) option("Choose")
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        payIfAsked()
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun utterItsNameMillsAndReturns() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(utterItsName)
        alice.library(bear, "Island", Dsk.ponder, ogre)
        alice.casts(utterItsName)
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.library.names shouldBe listOf(ogre)
        }
    }

    @Test
    fun twitchyDollMakesSpidersByItsCounters() = scenario {
        alice.battlefield(twitchyDoll, counters = listOf(CounterKind.Named("nest") to 2))
        alice.activates(twitchyDoll, ability = 1)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Spider Token" } shouldBe 2
            "Spider Token" hasKeyword Keyword.Reach
        }
    }

    @Test
    fun twitchyDollNestsAsItMakesMana() = scenario {
        alice.battlefield(twitchyDoll)
        alice.activates(twitchyDoll, ability = 0, color = Color.Blue)
        expect {
            alice.pool.size shouldBe 1
            twitchyDoll.counters(CounterKind.Named("nest")) shouldBe 1
        }
    }

    @Test
    fun tavikPumpsTheTeam() = scenario {
        alice.battlefield(tavikThePummeler, giant, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(tavikThePummeler, ability = 1)
        resolveAll()
        expect {
            tavikThePummeler.power shouldBe 8
            giant.power shouldBe 10
        }
    }

    @Test
    fun tavikTapsAFriendForIndestructible() = scenario {
        alice.battlefield(tavikThePummeler, bear)
        alice.activates(tavikThePummeler, ability = 0, choosing = listOf(bear))
        resolveAll()
        expect {
            tavikThePummeler hasKeyword Keyword.Indestructible
            tavikThePummeler.tapped shouldBe true
            bear.tapped shouldBe true
        }
    }

    // ---- Chunk 13: multicolored

    @Test
    fun annabelDrainsBySmallCreatures() = scenario {
        alice.battlefield(annabelForsakenDoll, bear, human, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(annabelForsakenDoll)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun cricketBatAttachesAndTapsABlocker() = scenario {
        alice.battlefield("Forest", "Plains", bear)
        alice.hand(cricketBat)
        bob.battlefield(giant)
        alice.casts(cricketBat)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targetsIfAsked(giant)
        resolveAll()
        expect { giant.tapped shouldBe true }
    }

    // CR 707.9b: a copy with exceptions; sacrificed at the next end step
    @Test
    fun theMerryBalloonManCopiesAsABalloon() = scenario {
        alice.battlefield(theMerryBalloonMan, giant, "Mountain")
        alice.activates(theMerryBalloonMan, target = giant)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == giant } shouldBe 2
            card(giant, 1).power shouldBe 1
            card(giant, 1) hasKeyword Keyword.Flying
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == giant } shouldBe 1 }
    }

    // CR 606.3: a loyalty ability
    @Test
    fun kaidenStunsACreature() = scenario {
        alice.battlefield(kaidenBaneOfDreams, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield(ogre)
        // its activated abilities: ninjutsu, then the three loyalty abilities
        alice.activates(kaidenBaneOfDreams, ability = 3, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 2
            kaidenBaneOfDreams.counters(CounterKind.Loyalty) shouldBe 2
        }
    }

    // CR 611.3a: a static ability with a condition makes it a creature
    @Test
    fun kaidenIsACreatureDuringYourTurn() = scenario {
        alice.battlefield(kaidenBaneOfDreams, counters = listOf(CounterKind.Loyalty to 4))
        expect {
            kaidenBaneOfDreams.power shouldBe 3
            kaidenBaneOfDreams hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun midnightMischiefMakesHastyGremlins() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains")
        alice.hand(midnightMischief)
        alice.casts(midnightMischief)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Gremlin Token" } shouldBe 3
            "Gremlin Token" hasKeyword Keyword.Haste
            "Gremlin Token" hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun nashkoMillsAndKeepsLegends() = scenario {
        alice.battlefield(nashkoSeekerInTheDark)
        alice.library(Dsk.charm, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(nashkoSeekerInTheDark)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses(Dsk.charm)
        resolveAll()
        expect {
            alice.hand has Dsk.charm
            alice.graveyard has bear
            nashkoSeekerInTheDark.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun nikaMakesShardsThatCopyACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", "Island", "Island", giant)
        alice.hand(nikaLightOfPromise)
        alice.casts(nikaLightOfPromise, paying = listOf("Plains", "Plains#1", "Island", "Island#1"))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Shard Token" } shouldBe 2 }
    }

    @Test
    fun riteOfTheLanternMothReturnsWithFinality() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(riteOfTheLanternMoth)
        alice.graveyard(giant)
        alice.casts(riteOfTheLanternMoth, target = giant)
        resolveAll()
        expect {
            alice.battlefield has giant
            giant.counters(CounterKind.Finality) shouldBe 1
        }
    }

    @Test
    fun sawtoothSkinflayerGrowsAndBurns() = scenario {
        alice.battlefield(sawtoothSkinflayer, charm, "Swamp", "Swamp")
        alice.activates(sawtoothSkinflayer, choosing = listOf(charm))
        resolveAll()
        expect { sawtoothSkinflayer.counters(p1p1) shouldBe 1 }
        advanceTo(TurnStep.End)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun undeadDasherReturnsAfterADeath() = scenario {
        alice.battlefield("Mountain", "Swamp", "Mountain")
        alice.hand(shock)
        alice.graveyard(undeadDasher)
        bob.battlefield(human)
        assertFailsWith<IllegalArgumentException> { alice.casts(undeadDasher) }
    }

    @Test
    fun undeadDasherCastFromTheGraveyardWithACounter() = scenario {
        alice.battlefield("Mountain", "Swamp", "Mountain")
        alice.hand(shock)
        alice.graveyard(undeadDasher)
        bob.battlefield(human)
        alice.casts(shock, target = human, paying = listOf("Mountain"))
        resolveAll()
        alice.casts(undeadDasher)
        resolveAll()
        expect {
            alice.battlefield has undeadDasher
            undeadDasher.counters(p1p1) shouldBe 1
        }
    }

    // a prime number of lands: three
    @Test
    fun zimraMakesPrima() = scenario {
        alice.battlefield(zimraAllWondering, "Forest", "Forest")
        alice.hand("Island")
        alice.plays("Island")
        advanceTo(TurnStep.End)
        resolveAll()
        expect { "Prima, the Undivided".power shouldBe 3 }
    }

    // ---- Chunk 14: artifacts and lands

    @Test
    fun pounceInTheBoxHitsHardThenGoes() = scenario {
        alice.battlefield(pounceInTheBox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pounceInTheBox)
        resolveAll()
        may(true)
        resolveAll()
        expect { pounceInTheBox.power shouldBe 6 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has pounceInTheBox }
    }

    @Test
    fun spiritVacuumExilesAndRaisesSpirits() = scenario {
        alice.battlefield(spiritVacuum)
        bob.graveyard(giant)
        alice.activates(spiritVacuum, ability = 0, target = giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun hauntedFoldingScreenBecomesASpirit() = scenario {
        alice.battlefield(hauntedFoldingScreen, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(hauntedFoldingScreen, ability = 2)
        resolveAll()
        expect {
            hauntedFoldingScreen.power shouldBe 7
            hauntedFoldingScreen.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
        }
    }

    @Test
    fun maliciousChandelierRecyclesAGraveyardCard() = scenario {
        alice.battlefield(maliciousChandelier, "Plains", "Plains")
        bob.graveyard(giant)
        bob.library(ogre)
        alice.activates(maliciousChandelier, target = giant)
        resolveAll()
        expect { bob.library.names shouldBe listOf(ogre, giant) }
    }

    // CR 613.1f: the activated abilities of other creatures
    @Test
    fun morvinBorrowsActivatedAbilities() = scenario {
        alice.battlefield(morvinMimickingMenace, tatteredPlaymate, bear, "Mountain")
        bob.battlefield(giant)
        alice.activates(morvinMimickingMenace, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun hacksawSacrificesForACard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear, charm)
        alice.hand(hacksaw)
        alice.library(ogre)
        alice.casts(hacksaw, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        alice.activates(hacksaw, target = bear, paying = listOf("Plains#2", "Plains#3"))
        resolveAll()
        expect { bear.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(charm)
        resolveAll()
        expect {
            alice.graveyard has charm
            alice.hand has ogre
        }
    }

    @Test
    fun forsakenCampsiteEntersUntappedAtLowLife() = scenario {
        alice.hand(forsakenCampsite)
        bob.life = 13
        alice.plays(forsakenCampsite)
        expect { forsakenCampsite.tapped shouldBe false }
    }

    @Test
    fun forsakenCampsiteEntersTappedOtherwise() = scenario {
        alice.hand(forsakenCampsite)
        alice.plays(forsakenCampsite)
        expect { forsakenCampsite.tapped shouldBe true }
    }

    @Test
    fun vulgarothsDenEntersTappedWithAColor() = scenario {
        alice.hand(vulgarothsDen)
        alice.plays(vulgarothsDen)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Black))
        expect {
            vulgarothsDen.tapped shouldBe true
            vulgarothsDen hasKeyword Keyword.Hexproof
        }
    }
}
