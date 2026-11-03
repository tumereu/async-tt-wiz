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
import arcanum.engine.sets.dsk.Dsk.curse
import arcanum.engine.sets.dsk.Dsk.giant
import arcanum.engine.sets.dsk.Dsk.human
import arcanum.engine.sets.dsk.Dsk.ogre
import arcanum.engine.sets.dsk.Dsk.p1p1
import arcanum.engine.sets.dsk.Dsk.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Duskmourn's other white, blue and black cards in play (parser chunks 09–10). */
class DskWhiteBlueBlackCardsScenariosTest {
    private val spectralPlating = Cards.invent("Spectral Plating", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 for each enchantment you control and has first strike.
    """)
    private val dreadOfAbduction = Cards.invent("Dread of Abduction", "{4}{W}{W}", "Enchantment Creature — Nightmare", """
        As an additional cost to cast this spell, exile a creature you control.
        Flying
        When this creature enters, exile target creature an opponent controls.
        When this creature leaves the battlefield, put each card exiled with it into its owner's hand.
    """, "5/5")
    private val dreadOfImmobility = Cards.invent("Dread of Immobility", "{4}{W}", "Enchantment Creature — Nightmare", "When this creature enters, tap up to one target creature. If an opponent controls that creature, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "4/4")
    private val steeledEscort = Cards.invent("Steeled Escort", "{2}{W}", "Creature — Human Soldier", "Whenever this creature attacks, another target creature you control gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)", "2/4")
    private val startleFright = Cards.invent("Startle Fright", "{W}", "Instant", "Until end of turn, target creature gets +2/+2, gains flying, and becomes a Horror enchantment creature in addition to its other types.")
    private val waifsOfTheWheat = Cards.invent("Waifs of the Wheat", "{1}{W}", "Creature — Human", "Whenever this creature attacks, tap any number of untapped creatures you control. This creature gets +1/+1 until end of turn for each creature tapped this way.", "2/1")
    private val stitchedPlaything = Cards.invent("Stitched Plaything", "{2}{W}", "Artifact Creature — Toy", """
        Double strike
        This creature enters with two -1/-1 counters on it if you cast it from your hand.
    """, "4/3")
    private val hauntedGoat = Cards.invent("Haunted Goat", "{W}", "Creature — Goat", "{3}, Discard a card: Put three +1/+1 counters on this creature and it becomes a black Demon in addition to its other colors and types. Activate only once.", "1/1")
    private val stolenFromSlumber = Cards.invent("Stolen from Slumber", "{4}{W}", "Instant", """
        This spell costs {3} less to cast if it targets a tapped creature.
        Destroy target creature.
    """)
    private val seamsplitDoll = Cards.invent("Seamsplit Doll", "{1}{W}", "Artifact Creature — Toy", "When this creature enters, draw a card. Then discard a card unless you control another creature with power 2 or less.", "2/1")
    private val tobinBeastieTamer = Cards.invent("Tobin, Beastie Tamer", "{2}{W}", "Legendary Creature — Human Wizard", """
        When Tobin enters, create a 4/4 white Beast creature token with "This token can't attack or block alone."
        As long as you control four or more creature tokens, creature tokens you control have flying.
    """, "1/1")
    private val clammyLurker = Cards.invent("Clammy Lurker", "{3}{U}", "Enchantment Creature — Horror", "Whenever this creature attacks, another target attacking creature can't be blocked this turn.", "2/5")
    private val notAPeep = Cards.invent("Not a Peep", "{1}{U}", "Instant", "Counter target spell unless its controller pays {2}. If they do, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)")
    private val duskmournsDominion = Cards.invent("Duskmourn's Dominion", "{4}{U}{U}", "Enchantment — Aura", """
        Enchant creature
        You control enchanted creature.
        Enchanted creature gets -3/-0 and loses all abilities.
    """)
    private val stepIntoTheEnigma = Cards.invent("Step into the Enigma", "{U}", "Sorcery", """
        Target creature can't be blocked this turn.
        Draw a card.
    """)
    private val dreadOfFailedExams = Cards.invent("Dread of Failed Exams", "{4}{U}", "Enchantment Creature — Nightmare", "Whenever this creature deals combat damage to a player, draw that many cards.", "2/7")
    private val dreadOfFalling = Cards.invent("Dread of Falling", "{3}{U}{U}", "Enchantment Creature — Nightmare", """
        Flying
        Whenever this creature attacks, target creature defending player controls gets -2/-0 and loses flying until your next turn.
    """, "4/4")
    private val floodpitsStrangler = Cards.invent("Floodpits Strangler", "{1}{U}", "Creature — Merfolk", """
        Flash
        Vigilance
        When this creature enters, tap target creature an opponent controls and put a stun counter on it.
        {1}{U}, {T}: Shuffle this creature and target creature with a stun counter on it into their owners' libraries.
    """, "2/1")
    private val getAway = Cards.invent("Get Away", "{U}{U}", "Instant", """
        Choose one —
        • Counter target creature or enchantment spell.
        • Return one or two target creatures and/or enchantments you own to your hand.
    """)
    private val mirelaVendanesGrimoire = Cards.invent("Mirela Vendane's Grimoire", "{5}{U}", "Legendary Artifact — Book", """
        When Mirela Vendane's Grimoire enters, if you cast it, draw five cards.
        You have no maximum hand size and don't lose the game for having 0 or less life.
        Whenever you gain life, draw that many cards.
        Whenever you lose life, discard that many cards. Then if you have no cards in hand, you lose the game.
    """)
    private val hushedHallcreeper = Cards.invent("Hushed Hallcreeper", "{1}{U}", "Enchantment Creature — Horror", """
        This creature can't be blocked.
        Whenever this creature deals combat damage to a player, choose one that hasn't been chosen —
        • Put two +1/+1 counters on this creature.
        • Draw a card.
        • This creature becomes a copy of another target creature you control.
    """, "1/1")
    private val goneFromSight = Cards.invent("Gone from Sight", "{3}{U}", "Instant", "Target nonland permanent's owner puts it on their choice of the top or bottom of their library. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    private val gigglingSlasher = Cards.invent("Giggling Slasher", "{3}{B}", "Creature — Human Assassin", """
        Deathtouch
        This creature enters with a +1/+1 counter on it if a creature died this turn.
    """, "3/3")
    private val returnGoneWrong = Cards.invent("Return Gone Wrong", "{2}{B}", "Sorcery", "Destroy target creature. If a creature card is put into a graveyard this way, return it to the battlefield under your control. Sacrifice it at the beginning of your next end step.")
    private val consultWithEvil = Cards.invent("Consult with Evil", "{2}{B}", "Sorcery", "Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard. You gain 3 life.")
    private val crackedCranium = Cards.invent("Cracked Cranium", "{2}{B}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, look at target player's hand. You may choose a nonland card from it. That player discards that card.
        When enchanted creature is dealt damage, destroy it.
    """)
    private val doomsdayEviscerator = Cards.invent("Doomsday Eviscerator", "{B}{B}{B}{B}{B}{B}", "Creature — Demon", """
        Flying
        When this creature enters, if it was cast, each player exiles all but the bottom six cards of their library face down.
        At the beginning of your upkeep, draw a card.
    """, "6/6")
    private val zealotOfTheHarrowing = Cards.invent("Zealot of the Harrowing", "{3}{B}", "Creature — Human Cleric", "When this creature enters, each player discards a card. If you discarded a card this way, draw a card.", "2/2")
    private val dreadOfTheDark = Cards.invent("Dread of the Dark", "{4}{B}", "Enchantment Creature — Nightmare", "Whenever this creature attacks, if defending player controls no Glimmer creatures, it gains menace and deathtouch until end of turn. (A creature with menace can't be blocked except by two or more creatures.)", "5/5")
    private val lastVengeance = Cards.invent("Last Vengeance", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature or enchantment.
        Exile target creature.
    """)
    private val grievousGash = Cards.invent("Grievous Gash", "{3}{B}{B}", "Enchantment — Aura", """
        Enchant player
        Enchanted player can't gain life.
        Whenever enchanted player is dealt damage, they lose half their life, rounded up.
    """)
    private val meathookMayhemII = Cards.invent("Meathook Mayhem II", "{X}{X}{B}{B}{B}{B}", "Legendary Enchantment", """
        When Meathook Mayhem II enters, each player sacrifices X creatures of their choice.
        Whenever a creature you control dies, you may pay 3 life. If you do, return that card under your control with a finality counter on it.
        Whenever a creature an opponent controls dies, they may pay 3 life. If they don't, return that card under your control with a finality counter on it.
    """)
    private val miasmaFiend = Cards.invent("Miasma Fiend", "{4}{B}{B}", "Creature — Demon", """
        Flying
        When this creature enters, you may discard any number of cards. When you do, up to that many target creatures each get -2/-2 until end of turn.
    """, "5/4")
    private val nowhereToHide = Cards.invent("Nowhere to Hide", "{1}{B}", "Enchantment", """
        Flash
        When this enchantment enters, target creature an opponent controls gets -3/-3 until end of turn.
        Creatures your opponents control can be the targets of spells and abilities as though they didn't have hexproof. Ward abilities of those creatures don't trigger.
    """)
    private val vainEgotist = Cards.invent("Vain Egotist", "{2}{B}", "Creature — Human Rogue", """
        {1}{B}, Sacrifice another creature or enchantment: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it.)
        Whenever you sacrifice a permanent, target opponent loses 1 life and you gain 1 life.
    """, "3/2")
    private val sporicInfection = Cards.invent("Sporic Infection", "{1}{B}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, target player sacrifices a creature of their choice other than enchanted creature.
        When enchanted creature is dealt damage, destroy it.
    """)
    private val unkillableSlasher = Cards.invent("Unkillable Slasher", "{2}{B}", "Creature — Zombie Assassin", """
        Deathtouch
        Whenever this creature deals combat damage to a player, they lose half their life, rounded up.
        When this creature dies, if it had no counters on it, return it to the battlefield tapped under its owner's control with two stun counters on it.
    """, "2/3")
    private val valgavothDreadDevourer = Cards.invent("Valgavoth, Dread Devourer", "{6}{B}{B}{B}", "Legendary Creature — Elder Demon", """
        Flying, lifelink
        Ward—Sacrifice three nonland permanents.
        If a card you didn't control would be put into an opponent's graveyard from anywhere, exile it instead.
        During your turn, you may play cards exiled with Valgavoth. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost.
    """, "9/9")
    private val vileMangler = Cards.invent("Vile Mangler", "{5}{B}{B}", "Creature — Demon", """
        As an additional cost to cast this spell, sacrifice a creature or enchantment.
        Flying, trample
        When this creature enters, each opponent sacrifices a nontoken enchantment of their choice, then sacrifices a nontoken creature of their choice.
    """, "6/5")
    private val spectralPilferer = Cards.invent("Spectral Pilferer", "{4}{B}{B}", "Creature — Spirit", """
        Ward—Discard a card. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player discards a card.)
        Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
    """, "6/5")

    // ---- Chunk 09: white and blue

    @Test
    fun spectralPlatingCountsEnchantments() = scenario {
        alice.battlefield("Plains", bear, charm, curse)
        alice.hand(spectralPlating)
        alice.casts(spectralPlating, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.FirstStrike
        }
    }

    // CR 610.3-like: cards exiled with it go to their owners' hands when it leaves
    @Test
    fun dreadOfAbductionExilesUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Swamp", human)
        alice.hand(dreadOfAbduction, Dsk.doom)
        bob.battlefield(ogre)
        alice.casts(dreadOfAbduction, choosing = listOf(human))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            alice.exile has human
            bob.exile has ogre
        }
        alice.casts(Dsk.doom, target = dreadOfAbduction)
        resolveAll()
        // the creature exiled to pay its cost was exiled with it too
        expect {
            bob.hand has ogre
            alice.hand has human
        }
    }

    @Test
    fun dreadOfImmobilityStunsAnOpponentsCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(dreadOfImmobility)
        bob.battlefield(ogre)
        alice.casts(dreadOfImmobility)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    @Test
    fun steeledEscortProtectsAnother() = scenario {
        alice.battlefield(steeledEscort, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(steeledEscort)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Indestructible
        }
    }

    // CR 205.1b: a Horror enchantment creature in addition to its other types
    @Test
    fun startleFrightMakesAHorror() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(startleFright)
        alice.casts(startleFright, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
            bear.types.toSet() shouldBe setOf(CardType.Enchantment, CardType.Creature)
            bear.subtypes.map { it.name }.contains("Horror") shouldBe true
        }
    }

    @Test
    fun waifsOfTheWheatTapsHelpers() = scenario {
        alice.battlefield(waifsOfTheWheat, bear, human)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(waifsOfTheWheat)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, human)
        resolveAll()
        expect {
            waifsOfTheWheat.power shouldBe 4
            bear.tapped shouldBe true
        }
    }

    // CR 614.1c: enters with counters only when cast from the hand
    @Test
    fun stitchedPlaythingEntersWeakenedFromTheHand() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(stitchedPlaything)
        alice.casts(stitchedPlaything)
        resolveAll()
        expect {
            stitchedPlaything.power shouldBe 2
            stitchedPlaything.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2
        }
    }

    // CR 602.5b: "Activate only once"
    @Test
    fun hauntedGoatBecomesADemon() = scenario {
        alice.battlefield(hauntedGoat, "Plains", "Plains", "Plains")
        alice.hand(bear)
        alice.activates(hauntedGoat, choosing = listOf(bear))
        resolveAll()
        expect {
            hauntedGoat.power shouldBe 4
            hauntedGoat.colors.toSet() shouldBe setOf(Color.White, Color.Black)
            hauntedGoat.subtypes.map { it.name }.contains("Demon") shouldBe true
        }
    }

    @Test
    fun stolenFromSlumberIsCheaperForATappedTarget() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(stolenFromSlumber)
        bob.battlefield(ogre, tapped = true)
        alice.casts(stolenFromSlumber, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun seamsplitDollDrawsAndKeepsWithASmallFriend() = scenario {
        alice.battlefield("Plains", "Plains", human)
        alice.hand(seamsplitDoll)
        alice.library(bear)
        alice.casts(seamsplitDoll)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun seamsplitDollLootsAlone() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(seamsplitDoll)
        alice.library(bear)
        alice.casts(seamsplitDoll)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    // CR 506.5: can't attack alone
    @Test
    fun tobinMakesABeastThatCantAttackAlone() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(tobinBeastieTamer)
        alice.casts(tobinBeastieTamer)
        resolveAll()
        expect { "Beast Token".power shouldBe 4 }
    }

    @Test
    fun clammyLurkerMakesAnAttackerUnblockable() = scenario {
        alice.battlefield(clammyLurker, bear)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(clammyLurker, bear)
        targetsIfAsked(bear)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    // CR 118.12: unless its controller pays; if they do, surveil 2
    @Test
    fun notAPeepCountersWhenUnpaid() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island")
        alice.hand(notAPeep)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(notAPeep, target = ogre)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun notAPeepSurveilsWhenPaid() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island")
        alice.hand(notAPeep)
        alice.library(giant, bear)
        turn(active = bob)
        bob.casts(ogre, paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        bob.passes()
        alice.casts(notAPeep, target = ogre)
        resolveAll()
        bob.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            alice.graveyard has giant
            bob.battlefield has ogre
        }
    }

    @Test
    fun duskmournsDominionStealsAndWeakens() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(duskmournsDominion)
        bob.battlefield(Dsk.flyer)
        alice.casts(duskmournsDominion, target = Dsk.flyer)
        resolveAll()
        expect {
            Dsk.flyer.controller shouldBe alice.id
            Dsk.flyer.power shouldBe -1
            Dsk.flyer lacksKeyword Keyword.Flying
        }
    }

    @Test
    fun stepIntoTheEnigmaMakesUnblockableAndDraws() = scenario {
        alice.battlefield("Island", bear)
        alice.hand(stepIntoTheEnigma)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(stepIntoTheEnigma, target = bear)
        resolveAll()
        expect { alice.hand has ogre }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun dreadOfFailedExamsDrawsByTheDamage() = scenario {
        alice.battlefield(dreadOfFailedExams)
        alice.library(bear, ogre, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dreadOfFailedExams)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 611.2a: "until your next turn"
    @Test
    fun dreadOfFallingGroundsAFlyer() = scenario {
        alice.battlefield(dreadOfFalling)
        bob.battlefield(Dsk.flyer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dreadOfFalling)
        targetsIfAsked(Dsk.flyer)
        resolveAll()
        expect {
            Dsk.flyer.power shouldBe 0
            Dsk.flyer lacksKeyword Keyword.Flying
        }
    }

    @Test
    fun floodpitsStranglerStunsOnEntering() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(floodpitsStrangler)
        bob.battlefield(ogre)
        alice.casts(floodpitsStrangler)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    // CR 701.24a: shuffled into their owners' libraries
    @Test
    fun floodpitsStranglerShufflesAStunnedCreatureAway() = scenario {
        alice.battlefield(floodpitsStrangler, "Island", "Island")
        bob.battlefield(ogre, tapped = true, counters = listOf(CounterKind.Stun to 1))
        alice.activates(floodpitsStrangler, target = ogre)
        resolveAll()
        expect {
            bob.library has ogre
            alice.library has floodpitsStrangler
        }
    }

    @Test
    fun getAwayReturnsYourOwnCreatures() = scenario {
        alice.battlefield("Island", "Island", bear, charm)
        alice.hand(getAway)
        alice.casts(getAway, modes = listOf(1), targets = listOf(bear, charm))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has charm
        }
    }

    @Test
    fun getAwayCountersACreatureSpell() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island")
        alice.hand(getAway)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(getAway, modes = listOf(0), target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    // CR 104.3b: no loss for 0 or less life while it says so
    @Test
    fun mirelaVendanesGrimoireDrawsFiveAndTradesLifeForCards() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Plains")
        alice.hand(mirelaVendanesGrimoire, Dsk.heal)
        alice.library(bear, ogre, giant, human, Dsk.flyer, Dsk.idol, Dsk.charm)
        alice.casts(mirelaVendanesGrimoire, paying = listOf("Island", "Island#1", "Island#2", "Island#3", "Island#4", "Island#5"))
        resolveAll()
        expect { alice.hand.size shouldBe 6 }
        alice.casts(Dsk.heal)
        resolveAll()
        expect { alice.hand.size shouldBe 7 }
    }

    // CR 700.2: "choose one that hasn't been chosen"
    @Test
    fun hushedHallcreeperChoosesANewModeEachTime() = scenario {
        alice.battlefield(hushedHallcreeper)
        alice.library(bear, ogre, giant)
        bob.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hushedHallcreeper)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        modes(0)
        resolveAll()
        expect { hushedHallcreeper.counters(p1p1) shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(hushedHallcreeper)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(0))) }
        modes(1)
        resolveAll()
        // the card of the turn's draw and the card drawn by the mode
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun goneFromSightPutsAPermanentOnTheBottom() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(goneFromSight)
        alice.library(bear)
        bob.battlefield(ogre)
        bob.library(giant)
        alice.casts(goneFromSight, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseOption) bob.answers(Answer.Option("bottom"))
        resolveAll()
        chooseNone()
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant, ogre) }
    }

    // ---- Chunk 10: black

    @Test
    fun gigglingSlasherEntersBiggerAfterADeath() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shock, gigglingSlasher)
        bob.battlefield(human)
        alice.casts(shock, target = human)
        resolveAll()
        alice.casts(gigglingSlasher)
        resolveAll()
        expect { gigglingSlasher.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun returnGoneWrongStealsForATurn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(returnGoneWrong)
        alice.library(bear)
        bob.battlefield(ogre)
        bob.library(giant)
        alice.casts(returnGoneWrong, target = ogre)
        resolveAll()
        expect { ogre.controller shouldBe alice.id }
        advanceTo(TurnStep.End, turn = 3)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun consultWithEvilKeepsOneAndGains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(consultWithEvil)
        alice.library(bear, ogre, giant, human, Dsk.flyer)
        alice.casts(consultWithEvil)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(giant)
            alice.graveyard.size shouldBe 4
            alice.library.names shouldBe listOf(Dsk.flyer)
            alice.life shouldBe 23
        }
    }

    // CR 120.3: dealt damage
    @Test
    fun crackedCraniumDiscardsAndDestroysOnDamage() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(crackedCranium, shock)
        bob.battlefield(giant)
        bob.hand(ogre)
        alice.casts(crackedCranium, target = giant)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
        alice.casts(shock, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun doomsdayEvisceratorShrinksTheLibraries() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(doomsdayEviscerator)
        alice.library("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.casts(doomsdayEviscerator)
        resolveAll()
        expect {
            alice.library.size shouldBe 6
            bob.library.size shouldBe 6
        }
    }

    @Test
    fun zealotOfTheHarrowingMakesEveryoneDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(zealotOfTheHarrowing, ogre)
        alice.library(bear)
        bob.hand(giant)
        alice.casts(zealotOfTheHarrowing)
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card(ogre))))
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card(giant))))
        resolveAll()
        expect {
            alice.graveyard has ogre
            bob.graveyard has giant
            alice.hand has bear
        }
    }

    @Test
    fun dreadOfTheDarkMenacesWithoutGlimmers() = scenario {
        alice.battlefield(dreadOfTheDark)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dreadOfTheDark)
        resolveAll()
        expect {
            dreadOfTheDark hasKeyword Keyword.Menace
            dreadOfTheDark hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun lastVengeanceSacrificesToExile() = scenario {
        alice.battlefield("Swamp", charm)
        alice.hand(lastVengeance)
        bob.battlefield(giant)
        alice.casts(lastVengeance, target = giant, choosing = listOf(charm))
        resolveAll()
        expect {
            bob.exile has giant
            alice.graveyard has charm
        }
    }

    // CR 119.7: can't gain life
    @Test
    fun grievousGashHalvesOnDamage() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(grievousGash, shock)
        alice.casts(grievousGash, target = "bob")
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 9 }
    }

    @Test
    fun meathookMayhemSacrificesAndReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(meathookMayhemII)
        bob.battlefield(ogre)
        alice.casts(meathookMayhemII, x = 1)
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.counters(CounterKind.Finality) shouldBe 1
            alice.graveyard has bear
        }
    }

    @Test
    fun miasmaFiendDiscardsToShrink() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(miasmaFiend, ogre)
        bob.battlefield(bear)
        alice.casts(miasmaFiend)
        resolveAll()
        may(true)
        alice.chooses(ogre)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    // CR 702.11b-like: targeted as though it didn't have hexproof
    @Test
    fun nowhereToHideShrinksAndIgnoresHexproof() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand(nowhereToHide, shock)
        bob.battlefield(bear, ogre)
        alice.casts(nowhereToHide)
        resolveAll()
        targets(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun vainEgotistDrainsOnASacrifice() = scenario {
        alice.battlefield(vainEgotist, charm, "Swamp", "Swamp")
        alice.activates(vainEgotist, choosing = listOf(charm))
        targetsIfAsked("bob")
        resolveAll()
        expect {
            vainEgotist hasKeyword Keyword.Indestructible
            vainEgotist.tapped shouldBe true
            bob.life shouldBe 19
        }
    }

    @Test
    fun sporicInfectionMakesThemSacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(sporicInfection)
        bob.battlefield(bear, ogre)
        alice.casts(sporicInfection, target = ogre)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.battlefield has ogre
        }
    }

    @Test
    fun unkillableSlasherReturnsStunned() = scenario {
        alice.battlefield("Swamp", unkillableSlasher)
        alice.hand(Dsk.doom)
        alice.casts(Dsk.doom, target = unkillableSlasher)
        resolveAll()
        expect {
            alice.battlefield has unkillableSlasher
            unkillableSlasher.tapped shouldBe true
            unkillableSlasher.counters(CounterKind.Stun) shouldBe 2
        }
    }

    @Test
    fun unkillableSlasherHalvesTheirLife() = scenario {
        alice.battlefield(unkillableSlasher)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(unkillableSlasher)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 9 }
    }

    // CR 614.1a: their cards exiled instead; cast with life
    @Test
    fun valgavothExilesTheirCardsAndPlaysThem() = scenario {
        alice.battlefield("Mountain", valgavothDreadDevourer)
        alice.hand(shock)
        bob.battlefield(bear)
        alice.casts(shock, target = bear)
        resolveAll()
        expect { bob.exile has bear }
        alice.casts(bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.life shouldBe 18
        }
    }

    @Test
    fun vileManglerMakesThemSacrificeTwice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", human)
        alice.hand(vileMangler)
        bob.battlefield(charm, ogre)
        alice.casts(vileMangler, choosing = listOf(human))
        resolveAll()
        expect {
            bob.graveyard has charm
            bob.graveyard has ogre
        }
    }

    @Test
    fun spectralPilfererSwampcycles() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(spectralPilferer)
        alice.library(bear, "Swamp")
        alice.activates(spectralPilferer)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Swamp")
        resolveAll()
        expect { alice.hand has "Swamp" }
    }
}
