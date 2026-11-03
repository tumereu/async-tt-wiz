package arcanum.engine.sets.blb

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.boar
import arcanum.engine.sets.blb.Blb.food
import arcanum.engine.sets.blb.Blb.giant
import arcanum.engine.sets.blb.Blb.idol
import arcanum.engine.sets.blb.Blb.mouse
import arcanum.engine.sets.blb.Blb.ogre
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.sets.blb.Blb.ponder
import arcanum.engine.sets.blb.Blb.rabbit
import arcanum.engine.sets.blb.Blb.raccoon
import arcanum.engine.sets.blb.Blb.shock
import arcanum.engine.sets.blb.Blb.squirrel
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's forage, Food, valiant and expend cards in play (parser chunks 03–04), beyond the mechanics played in [BlbGiftAndForageScenariosTest] and [BlbValiantAndExpendScenariosTest]. */
class BlbFoodValiantAndExpendCardsScenariosTest {
    private val fluffytailGuard = Cards.invent("Fluffytail Guard", "{1}{G}", "Creature — Squirrel Warrior", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature enters, you may forage. If you do, put two +1/+1 counters on it. (To forage, exile three cards from your graveyard or sacrifice a Food.)
    """, "2/1")
    private val nosyGatherer = Cards.invent("Nosy Gatherer", "{2}{G}", "Creature — Squirrel Druid", """
        When this creature enters, you may forage. When you do, return target permanent card from your graveyard to your hand. (To forage, exile three cards from your graveyard or sacrifice a Food.)
    """, "3/2")
    private val bramblerootGatherer = Cards.invent("Brambleroot Gatherer", "{1}{G}", "Creature — Squirrel Ranger", """
        {T}: Add {G}.
        {T}, Forage: Add two mana in any combination of colors. (To forage, exile three cards from your graveyard or sacrifice a Food.)
        {3}{G}, {T}: Search your library for a Squirrel card, reveal it, put it into your hand, then shuffle.
    """, "2/2")
    private val petuniaTheAcornhoarder = Cards.invent("Petunia, the Acornhoarder", "{1}{B}{G}", "Legendary Creature — Squirrel Warlock", """
        Menace
        Other Squirrels you control have menace.
        Whenever you sacrifice one or more Foods, create a 1/1 green Squirrel creature token.
        {2}, Forage: Put a +1/+1 counter on each other Squirrel you control. (To forage, exile three cards from your graveyard or sacrifice a Food.)
    """, "3/3")
    private val gorvaxDevourerOfEverything = Cards.invent("Gorvax, Devourer of Everything", "{3}{B}{G}", "Legendary Creature — Elemental Cat", """
        Ward—Sacrifice a Food.
        Other creatures are Food artifacts in addition to their other types and have "{2}, {T}, Sacrifice this permanent: You gain 3 life."
        Whenever a Food is put into a graveyard from the battlefield, put two +1/+1 counters on Gorvax.
    """, "6/6")
    private val marrowhoardWarden = Cards.invent("Marrowhoard Warden", "{B}", "Creature — Squirrel Warlock", "{T}, Pay 1 life: Draw a card. Activate only if three or more cards left your graveyard this turn or if you've sacrificed a Food this turn.", "1/1")
    private val candiedShell = Cards.invent("Candied Shell", "{2}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature or Food
        Enchanted permanent is a colorless Food artifact with "{2}, {T}, Sacrifice this artifact: You gain 3 life" and loses all other card types and abilities.
    """)
    private val reveredNestwarden = Cards.invent("Revered Nestwarden", "{2}{G}", "Creature — Squirrel Warrior", """
        Trample
        When this creature enters, put a +1/+1 counter on it for each other Squirrel and/or Food you control.
        Whenever another Squirrel or Food you control enters, put a +1/+1 counter on this creature.
    """, "1/1")
    private val turnipTart = Cards.invent("Turnip Tart", "{1}{W}", "Artifact — Food", """
        When this artifact enters and when you sacrifice it, create a 1/1 white Rabbit creature token and scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """)
    private val bountifulBasket = Cards.invent("Bountiful Basket", "{2}{G}", "Artifact — Food", """
        When this artifact enters and when you sacrifice it, you may search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """)
    private val stashSnatch = Cards.invent("Stash Snatch", "{1}{G}", "Instant", """
        Mill four cards. You may put a permanent card from among the cards milled this way into your hand. If you control a Squirrel or returned a Squirrel card to your hand this way, create a Food token. (To mill four cards, put the top four cards of your library into your graveyard. A Food token is an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """)
    private val relish = Cards.invent("Relish", "{1}{B}", "Instant", """
        Target creature gets -2/-2 until end of turn. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """)
    private val vinepickerElder = Cards.invent("Vinepicker Elder", "{B}{G}", "Creature — Squirrel Druid", """
        When this creature enters or dies, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """, "3/2")
    private val honeyblossomsTeapot = Cards.invent("Honeyblossom's Teapot", "{2}", "Artifact", """
        When this artifact enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        {5}, {T}, Sacrifice this artifact: Destroy target nonland permanent. Activate only as a sorcery.
    """)
    private val cloverleafPhalanx = Cards.invent("Cloverleaf Phalanx", "{1}{G}", "Instant", """
        Choose one —
        • Destroy target creature with flying.
        • Destroy target enchantment.
        • Draw a card. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """)
    private val petalstepDuelist = Cards.invent("Petalstep Duelist", "{W}", "Creature — Mouse Soldier", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, Mice you control get +1/+0 until end of turn.
    """, "1/2")
    private val whiskerSnarer = Cards.invent("Whisker Snarer", "{2}{W}", "Creature — Mouse Soldier", """
        Flash
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, tap target creature an opponent controls.
    """, "3/2")
    private val thistleSentry = Cards.invent("Thistle Sentry", "{1}{W}", "Creature — Mouse Soldier", """
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, it gets +0/+2 until end of turn.
        {1}, Sacrifice this creature: Destroy target artifact or enchantment.
    """, "3/1")
    private val whiskerdalePathfinder = Cards.invent("Whiskerdale Pathfinder", "{3}{W}", "Creature — Mouse Bard", """
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, look at the top five cards of your library. You may reveal a creature card with mana value 3 or less from among them. You may put it onto the battlefield if it's your turn. If you don't put it onto the battlefield, put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "3/4")
    private val cinderheartContender = Cards.invent("Cinderheart Contender", "{1}{R}", "Creature — Mouse Warrior", """
        Haste
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, exile the top card of your library. Until end of turn, you may play that card.
    """, "2/2")
    private val inkwhiskerClerk = Cards.invent("Inkwhisker Clerk", "{1}{R}", "Creature — Mouse Citizen", """
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, you may discard a card. If you do, draw a card.
    """, "2/2")
    private val acornbladeTutor = Cards.invent("Acornblade Tutor", "{1}{R}{W}", "Creature — Mouse Soldier", """
        Vigilance, haste
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, put a +1/+1 counter on it.
    """, "3/2")
    private val grizzledMousewarden = Cards.invent("Grizzled Mousewarden", "{3}{R/W}", "Creature — Mouse Soldier", """
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, it gets +1/+0 and gains first strike until end of turn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
    """, "3/4")
    private val backroadHaggler = Cards.invent("Backroad Haggler", "{2}{R}", "Creature — Raccoon Rogue", """
        Menace
        Whenever you expend 4, you may discard your hand. If you do, draw two cards. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "3/3")
    private val packratsSurplus = Cards.invent("Packrat's Surplus", "{1}{R}", "Enchantment", """
        When this enchantment enters and whenever you expend 4, put a stash counter on it. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        {1}{R}, Sacrifice this enchantment: Discard your hand, then draw cards equal to the number of stash counters on this enchantment.
    """)
    private val rowdyPair = Cards.invent("Rowdy Pair", "{2}{R}", "Creature — Mouse Raccoon", """
        Trample
        Whenever you expend 4, target creature you control gets +1/+1 and gains trample until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "3/2")
    private val pastrythiefPair = Cards.invent("Pastrythief Pair", "{1}{G}", "Creature — Squirrel Raccoon", """
        When this creature enters, create a Food token.
        Whenever you expend 4, this creature gets +1/+1 until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "2/2")
    private val oakFistBrawler = Cards.invent("Oak-Fist Brawler", "{1}{G}", "Creature — Raccoon Berserker", """
        Whenever you expend 4, this creature gains indestructible until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "3/2")
    private val thornhedgeOldTimer = Cards.invent("Thornhedge Old-Timer", "{1}{G}{G}", "Creature — Raccoon Warrior", """
        Whenever you expend 4, Raccoons you control get +1/+1 and gain vigilance until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "3/4")
    private val scrapswordThug = Cards.invent("Scrapsword Thug", "{3}{R/G}{R/G}", "Creature — Raccoon Berserker", """
        Trample
        Whenever you expend 4, this creature gets +2/+1 until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "4/5")
    private val rizzoRubbishStrategist = Cards.invent("Rizzo, Rubbish Strategist", "{1}{R}{G}", "Legendary Creature — Raccoon Warrior", """
        At the beginning of your first main phase, add {R} or {G} for each Raccoon you control.
        Whenever you expend 4, you gain 3 life. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        Whenever you expend 8, exile the top two cards of your library. Until the end of your next turn, you may play those cards.
    """, "2/4")
    private val roamstoryElder = Cards.invent("Roamstory Elder", "{R}{G}", "Creature — Raccoon Bard", """
        Whenever you expend 4, put a +1/+1 counter on this creature. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        {T}: Add {R} or {G}.
    """, "2/2")

    // ---- Chunk 03: forage and Food (forage itself: BlbGiftAndForageScenariosTest)

    // CR 701.61a: foraging by sacrificing the Food
    @Test
    fun fluffytailGuardForagesForCounters() = scenario {
        alice.battlefield("Forest", "Forest", food)
        alice.hand(fluffytailGuard)
        alice.casts(fluffytailGuard, kicked = 0)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            fluffytailGuard.counters(p1p1) shouldBe 2
            alice.graveyard has food
        }
    }

    // CR 603.12: "when you do" is a reflexive trigger
    @Test
    fun nosyGathererForagesToReturnAPermanentCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", food)
        alice.hand(nosyGatherer)
        alice.graveyard(idol)
        alice.casts(nosyGatherer)
        resolveAll()
        may(true)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect { alice.hand has idol }
    }

    @Test
    fun bramblerootGathererFindsASquirrel() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bramblerootGatherer)
        alice.library(bear, squirrel)
        alice.activates(bramblerootGatherer, ability = 2)
        resolveAll()
        alice.chooses(squirrel)
        resolveAll()
        expect {
            alice.hand has squirrel
            alice.library has bear
        }
    }

    // CR 605.1a: a mana ability with a forage cost
    @Test
    fun bramblerootGathererForagesForTwoMana() = scenario {
        alice.battlefield(bramblerootGatherer, food)
        alice.activates(bramblerootGatherer, ability = 1, color = Color.Black)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Black))
        expect {
            alice.pool.size shouldBe 2
            alice.graveyard has food
        }
    }

    // CR 701.61a: foraging by sacrificing a Food is sacrificing it
    @Test
    fun petuniaTheAcornhoarderFeedsTheSquirrels() = scenario {
        alice.battlefield("Forest", "Forest", petuniaTheAcornhoarder, squirrel, food)
        alice.activates(petuniaTheAcornhoarder)
        resolveAll()
        expect {
            squirrel hasKeyword Keyword.Menace
            alice.battlefield has "Squirrel Token"
            squirrel.counters(p1p1) shouldBe 1
            card("Squirrel Token").counters(p1p1) shouldBe 1
            petuniaTheAcornhoarder.counters(p1p1) shouldBe 0
        }
    }

    // CR 205.1b: Food artifact in addition to its other types, with the Food's ability
    @Test
    fun gorvaxMakesFoodOfTheOthers() = scenario {
        alice.battlefield("Forest", "Forest", gorvaxDevourerOfEverything, bear)
        expect { bear.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature) }
        alice.activates(bear)
        resolveAll()
        expect {
            alice.life shouldBe 23
            gorvaxDevourerOfEverything.counters(p1p1) shouldBe 2
        }
    }

    // CR 602.5b: the activation restriction
    @Test
    fun marrowhoardWardenDrawsAfterAFoodWasSacrificed() = scenario {
        alice.battlefield("Swamp", "Swamp", marrowhoardWarden, food)
        alice.library(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(marrowhoardWarden) }
        alice.activates(food)
        resolveAll()
        alice.activates(marrowhoardWarden)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 22
        }
    }

    // CR 613.1d, f: type-changing and ability-removing effects
    @Test
    fun candiedShellTurnsACreatureIntoFood() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(candiedShell)
        bob.battlefield(Blb.flyer)
        alice.casts(candiedShell, target = Blb.flyer)
        resolveAll()
        expect {
            Blb.flyer.types shouldBe listOf(CardType.Artifact)
            Blb.flyer.colors shouldBe emptyList()
            Blb.flyer lacksKeyword Keyword.Flying
        }
    }

    @Test
    fun reveredNestwardenCountsSquirrelsAndFood() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", squirrel, food)
        alice.hand(reveredNestwarden, squirrel)
        alice.casts(reveredNestwarden)
        resolveAll()
        expect { reveredNestwarden.counters(p1p1) shouldBe 2 }
        alice.casts(squirrel)
        resolveAll()
        expect { reveredNestwarden.counters(p1p1) shouldBe 3 }
    }

    // CR 603.6a, 603.10a: "when you sacrifice it" triggers as it leaves
    @Test
    fun turnipTartMakesRabbitsOnEnteringAndSacrifice() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(turnipTart)
        alice.library(bear, ogre)
        alice.casts(turnipTart)
        resolveAll()
        chooseNone()
        resolveAll()
        alice.activates(turnipTart)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 2
            alice.life shouldBe 23
        }
    }

    @Test
    fun bountifulBasketFetchesABasicLand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(bountifulBasket)
        alice.library(bear, "Plains")
        alice.casts(bountifulBasket)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
        }
    }

    // CR 701.17a: mill; "returned a Squirrel card this way" makes a Food
    @Test
    fun stashSnatchKeepsAMilledSquirrel() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(stashSnatch)
        alice.library(bear, squirrel, "Island", ponder, ogre)
        alice.casts(stashSnatch)
        resolveAll()
        alice.chooses(squirrel)
        resolveAll()
        expect {
            alice.hand has squirrel
            alice.graveyard has bear
            alice.library.names shouldBe listOf(ogre)
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun stashSnatchWithoutASquirrelMakesNoFood() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(stashSnatch)
        alice.library(bear, "Island", ponder, ogre)
        alice.casts(stashSnatch)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield lacks "Food Token"
        }
    }

    @Test
    fun relishShrinksAndMakesFood() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(relish)
        bob.battlefield(bear)
        alice.casts(relish, target = bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun vinepickerElderMakesFoodOnEnteringAndDying() = scenario {
        alice.battlefield("Swamp", "Forest", "Mountain")
        alice.hand(vinepickerElder, shock)
        alice.casts(vinepickerElder)
        resolveAll()
        alice.casts(shock, target = vinepickerElder)
        resolveAll()
        expect {
            alice.graveyard has vinepickerElder
            alice.battlefield.names.count { it == "Food Token" } shouldBe 2
        }
    }

    @Test
    fun honeyblossomsTeapotMakesFoodAndDestroys() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(honeyblossomsTeapot)
        bob.battlefield(ogre)
        alice.casts(honeyblossomsTeapot)
        resolveAll()
        expect { alice.battlefield has "Food Token" }
        alice.activates(honeyblossomsTeapot, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has honeyblossomsTeapot
        }
    }

    @Test
    fun cloverleafPhalanxDestroysAFlyer() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(cloverleafPhalanx)
        bob.battlefield(Blb.flyer)
        alice.casts(cloverleafPhalanx, modes = listOf(0), target = Blb.flyer)
        resolveAll()
        expect { bob.graveyard has Blb.flyer }
    }

    @Test
    fun cloverleafPhalanxDrawsAndMakesFood() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(cloverleafPhalanx)
        alice.library(bear)
        alice.casts(cloverleafPhalanx, modes = listOf(2))
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield has "Food Token"
        }
    }

    // ---- Chunk 04: valiant and expend (the keywords themselves: BlbValiantAndExpendScenariosTest)

    // CR 207.2c: valiant is an ability word; CR 115.10: the creature becomes the target of your spell
    @Test
    fun petalstepDuelistRalliesTheMice() = scenario {
        alice.battlefield("Forest", petalstepDuelist, mouse)
        alice.hand(Blb.pump)
        alice.casts(Blb.pump, target = petalstepDuelist)
        resolveAll()
        expect {
            mouse.power shouldBe 2
            petalstepDuelist.power shouldBe 3
        }
    }

    @Test
    fun whiskerSnarerTapsACreature() = scenario {
        alice.battlefield("Forest", whiskerSnarer)
        alice.hand(Blb.pump)
        bob.battlefield(ogre)
        alice.casts(Blb.pump, target = whiskerSnarer)
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun thistleSentryToughensUp() = scenario {
        alice.battlefield("Forest", thistleSentry)
        alice.hand(Blb.pump)
        alice.casts(Blb.pump, target = thistleSentry)
        resolveAll()
        expect { thistleSentry.toughness shouldBe 4 }
    }

    @Test
    fun thistleSentrySacrificesToDestroyAnArtifact() = scenario {
        alice.battlefield("Plains", thistleSentry)
        bob.battlefield(idol)
        alice.activates(thistleSentry, target = idol)
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.graveyard has thistleSentry
        }
    }

    @Test
    fun whiskerdalePathfinderPutsACreatureOntoTheBattlefield() = scenario {
        alice.battlefield("Forest", whiskerdalePathfinder)
        alice.hand(Blb.pump)
        alice.library(giant, bear, "Island", ponder, ogre, rabbit)
        alice.casts(Blb.pump, target = whiskerdalePathfinder)
        resolveAll()
        alice.chooses(bear)
        may(true)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.library.names.first() shouldBe rabbit
        }
    }

    @Test
    fun cinderheartContenderExilesAPlayableCard() = scenario {
        alice.battlefield("Forest", cinderheartContender)
        alice.hand(Blb.pump)
        alice.library("Mountain")
        alice.casts(Blb.pump, target = cinderheartContender)
        resolveAll()
        expect { cinderheartContender.power shouldBe 4 }
        alice.plays("Mountain")
        expect { alice.battlefield has "Mountain" }
    }

    @Test
    fun inkwhiskerClerkRummages() = scenario {
        alice.battlefield("Forest", inkwhiskerClerk)
        alice.hand(Blb.pump, ogre)
        alice.library(bear)
        alice.casts(Blb.pump, target = inkwhiskerClerk)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand has bear
        }
    }

    @Test
    fun acornbladeTutorGetsACounter() = scenario {
        alice.battlefield("Forest", acornbladeTutor)
        alice.hand(Blb.pump)
        alice.casts(Blb.pump, target = acornbladeTutor)
        resolveAll()
        expect { acornbladeTutor.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun grizzledMousewardenStrikesFirstAndScries() = scenario {
        alice.battlefield("Forest", grizzledMousewarden)
        alice.hand(Blb.pump)
        alice.library(bear, ogre)
        alice.casts(Blb.pump, target = grizzledMousewarden)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            grizzledMousewarden.power shouldBe 5
            grizzledMousewarden hasKeyword Keyword.FirstStrike
            alice.library.names shouldBe listOf(ogre, bear)
        }
    }

    // CR 700.14: expend 4
    @Test
    fun backroadHagglerTradesTheHandForTwo() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", backroadHaggler)
        alice.hand(boar, ponder)
        alice.library(bear, ogre)
        alice.casts(boar)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.graveyard has ponder
            alice.hand.names shouldBe listOf(bear, ogre)
        }
    }

    @Test
    fun packratsSurplusStashesAndCashesIn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Forest", "Forest", "Forest", "Forest")
        alice.hand(packratsSurplus, boar, ponder)
        alice.library(bear, ogre, rabbit)
        alice.casts(packratsSurplus, paying = listOf("Mountain#0", "Mountain#1"))
        resolveAll()
        alice.casts(boar, paying = listOf("Forest#0", "Forest#1", "Forest#2", "Forest#3"))
        resolveAll()
        expect { packratsSurplus.counters(CounterKind.Named("stash")) shouldBe 2 }
        alice.activates(packratsSurplus, paying = listOf("Mountain#2", "Mountain#3"))
        resolveAll()
        expect {
            alice.graveyard has ponder
            alice.hand.names shouldBe listOf(bear, ogre)
        }
    }

    @Test
    fun rowdyPairPumpsACreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", rowdyPair, bear)
        alice.hand(boar)
        alice.casts(boar)
        targets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun pastrythiefPairMakesFoodAndGrows() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(pastrythiefPair, bear)
        alice.casts(pastrythiefPair)
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect {
            alice.battlefield has "Food Token"
            pastrythiefPair.power shouldBe 3
        }
    }

    @Test
    fun oakFistBrawlerTurnsIndestructible() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", oakFistBrawler)
        alice.hand(boar)
        alice.casts(boar)
        resolveAll()
        expect { oakFistBrawler hasKeyword Keyword.Indestructible }
    }

    @Test
    fun thornhedgeOldTimerRalliesTheRaccoons() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", thornhedgeOldTimer, raccoon)
        alice.hand(boar)
        alice.casts(boar)
        resolveAll()
        expect {
            raccoon.power shouldBe 3
            raccoon hasKeyword Keyword.Vigilance
            thornhedgeOldTimer.power shouldBe 4
            boar.power shouldBe 4
        }
    }

    @Test
    fun scrapswordThugGrows() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", scrapswordThug)
        alice.hand(boar)
        alice.casts(boar)
        resolveAll()
        expect {
            scrapswordThug.power shouldBe 6
            scrapswordThug.toughness shouldBe 6
        }
    }

    @Test
    fun rizzoGainsLifeOnExpendFour() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", rizzoRubbishStrategist)
        alice.hand(boar)
        alice.casts(boar)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 505.1: the first main phase of the turn
    @Test
    fun rizzoAddsManaPerRaccoon() = scenario {
        alice.battlefield(rizzoRubbishStrategist, raccoon)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        if (pending is Decision.ChooseOption) option("{G}")
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        if (pending is Decision.ChooseOption) option("{G}")
        expect { alice.pool.size shouldBe 2 }
    }

    // CR 700.14: expend 8
    @Test
    fun rizzoExilesTwoOnExpendEight() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", rizzoRubbishStrategist)
        alice.hand(boar, boar)
        alice.library("Mountain", ogre)
        alice.casts(boar)
        resolveAll()
        alice.casts(boar)
        resolveAll()
        expect {
            alice.exile has "Mountain"
            alice.exile has ogre
        }
        alice.plays("Mountain")
        expect { alice.battlefield has "Mountain" }
    }

    @Test
    fun roamstoryElderGrowsOnExpend() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", roamstoryElder)
        alice.hand(boar)
        alice.casts(boar, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3"))
        resolveAll()
        expect { roamstoryElder.counters(p1p1) shouldBe 1 }
    }
}
