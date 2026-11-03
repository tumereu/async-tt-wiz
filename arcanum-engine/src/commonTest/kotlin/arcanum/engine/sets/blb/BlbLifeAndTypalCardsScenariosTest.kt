package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bat
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.bird
import arcanum.engine.sets.blb.Blb.flyer
import arcanum.engine.sets.blb.Blb.frog
import arcanum.engine.sets.blb.Blb.giant
import arcanum.engine.sets.blb.Blb.heal
import arcanum.engine.sets.blb.Blb.lizard
import arcanum.engine.sets.blb.Blb.mouse
import arcanum.engine.sets.blb.Blb.ogre
import arcanum.engine.sets.blb.Blb.otter
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.sets.blb.Blb.pact
import arcanum.engine.sets.blb.Blb.ponder
import arcanum.engine.sets.blb.Blb.rabbit
import arcanum.engine.sets.blb.Blb.rabbitCall
import arcanum.engine.sets.blb.Blb.rat
import arcanum.engine.sets.blb.Blb.shock
import arcanum.engine.sets.blb.Blb.squirrel
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's life gain and loss cards, typal creatures and Hamlets in play (parser chunks 07–08). */
class BlbLifeAndTypalCardsScenariosTest {
    private val lifesapConduit = Cards.invent("Lifesap Conduit", "{1}{W}", "Creature — Bat Cleric", """
        As long as you've lost life this turn, this creature has flying and vigilance.
        Whenever you gain life, put a +1/+1 counter on this creature.
        When this creature dies, put its counters on target creature you control.
    """, "2/1")
    private val constellationMapper = Cards.invent("Constellation Mapper", "{3}{W}", "Creature — Bat Cleric", """
        Flying
        At the beginning of your end step, if you gained or lost life this turn, look at the top four cards of your library. You may reveal a creature card with power 3 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "3/1")
    private val quartzknifeKiller = Cards.invent("Quartzknife Killer", "{1}{B}", "Creature — Lizard Assassin", "Whenever this creature attacks, defending player loses 1 life and you gain 1 life.", "1/3")
    private val swoopwingPair = Cards.invent("Swoopwing Pair", "{4}{B}", "Creature — Bat Lizard", """
        Flying
        When this creature enters, each opponent loses 2 life and you gain 2 life.
    """, "3/3")
    private val hestersLullaby = Cards.invent("Hester's Lullaby", "{3}{B}", "Instant", "Return up to two target creature cards from your graveyard to your hand. Each opponent loses 2 life and you gain 2 life.")
    private val lunastoneHerald = Cards.invent("Lunastone Herald", "{2}{B}", "Creature — Bat Warrior", """
        Flying, deathtouch
        Whenever you gain or lose life during your turn, Bats you control get +1/+0 and gain deathtouch until end of turn. This ability triggers only once each turn.
    """, "1/3")
    private val ominousObelisk = Cards.invent("Ominous Obelisk", "{3}{B}", "Artifact", """
        At the beginning of combat on your turn, each opponent loses 1 life and you gain 1 life.
        {T}, Pay 2 life, Sacrifice this artifact: Draw two cards. Activate only as a sorcery.
    """)
    private val starlightDiviner = Cards.invent("Starlight Diviner", "{2}{B}", "Creature — Bat Cleric", """
        Flying
        At the beginning of your end step, if you gained or lost life this turn, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "2/2")
    private val mindprowlHexer = Cards.invent("Mindprowl Hexer", "{2}{B}", "Creature — Lizard Warlock", """
        Menace
        When this creature enters, choose target opponent. If they lost life this turn, they reveal their hand, you choose a nonland card from it, and they discard that card. Otherwise, they discard a card.
    """, "2/2")
    private val embercacheNewt = Cards.invent("Embercache Newt", "{1}{R}", "Creature — Lizard Warlock", """
        When this creature enters, if an opponent lost life this turn, add {B}{R}.
        {1}{R}, Discard a card: Draw a card.
    """, "2/2")
    private val paidTalon = Cards.invent("Paid Talon", "{R}", "Creature — Lizard Mercenary", """
        Whenever you attack with one or more Lizards, this creature deals 1 damage to target opponent.
        {1}{R}: Put a +1/+1 counter on this creature. Activate only if an opponent lost life this turn and only once each turn.
    """, "1/2")
    private val smolderingThroatslitter = Cards.invent("Smoldering Throatslitter", "{2}{B/R}", "Creature — Lizard Assassin", """
        This creature enters with a +1/+1 counter on it if an opponent lost life this turn.
        {1}{B/R}: This creature gains menace until end of turn.
    """, "3/2")
    private val kilnglassTutor = Cards.invent("Kilnglass Tutor", "{B}{R}", "Creature — Lizard Warlock", "At the beginning of your second main phase, if an opponent lost life this turn, exile the top two cards of your library. Choose one of them. Until end of turn, you may play that card.", "2/1")
    private val vexxScorchedScale = Cards.invent("Vexx, Scorched Scale", "{B}{R}", "Legendary Creature — Lizard Mercenary", """
        Ward—Pay 2 life.
        Other creatures you control enter with an additional +1/+1 counter on them for each opponent who lost life this turn.
        Whenever you cast a Lizard spell, Vexx deals 1 damage to target opponent.
    """, "3/2")
    private val moonlitAssembly = Cards.invent("Moonlit Assembly", "{W}{B}", "Enchantment", """
        At the beginning of your end step, if you gained life this turn, each opponent loses 1 life.
        At the beginning of your end step, if you gained and lost life this turn, create a 1/1 black Bat creature token with flying.
        {1}{B}, Pay 2 life: Draw a card.
    """)
    private val duskrisePriest = Cards.invent("Duskrise Priest", "{1}{W/B}{W/B}", "Creature — Bat Cleric", """
        Flying
        Whenever this creature attacks, you gain 1 life.
    """, "2/3")
    private val skywatchElder = Cards.invent("Skywatch Elder", "{3}{W}{B}", "Creature — Bat Warlock", """
        Flying, vigilance
        At the beginning of your end step, if you gained or lost life this turn, target opponent loses 3 life unless they sacrifice a nonland permanent of their choice or discard a card.
    """, "3/5")
    private val noctaraStarcaller = Cards.invent("Noctara, Starcaller", "{1}{W}{B}", "Legendary Creature — Bat Cleric", """
        Flying, vigilance
        Whenever a Bat you control attacks, you gain 1 life.
        Whenever Noctara enters or attacks, you may pay {W}{B} and 2 life. When you do, return target nonland permanent card with mana value 3 or less from your graveyard to the battlefield with a finality counter on it.
    """, "3/3")
    private val reapriteHostess = Cards.invent("Reaprite Hostess", "{2}{W}", "Creature — Rabbit Citizen", "Whenever this creature or another Rabbit you control enters, target creature you control gets +1/+0 until end of turn. Then draw a card if this is the second time this ability has resolved this turn.", "3/3")
    private val magpieRescuer = Cards.invent("Magpie Rescuer", "{2}{W}", "Creature — Bird Cleric", """
        Flying
        Whenever this creature or another creature you control with flying dies, return another target creature card with lesser mana value from your graveyard to the battlefield.
    """, "3/1")
    private val crestedQuartermaster = Cards.invent("Crested Quartermaster", "{4}{W}", "Creature — Bird Scout", """
        Flying
        When this creature enters, put a +1/+1 counter on target creature you control without flying.
    """, "3/4")
    private val deliveranceHeron = Cards.invent("Deliverance Heron", "{3}{W}", "Creature — Bird Cleric", """
        Flash
        Flying
        Whenever this creature or another Bird you control enters, exile up to one target creature you control without flying. Return it to the battlefield under its owner's control with a flying counter on it at the beginning of the next end step.
    """, "3/3")
    private val veteranBurrowguard = Cards.invent("Veteran Burrowguard", "{W}", "Creature — Rabbit Warrior", "Whenever this creature attacks while you control a token, this creature gets +2/+0 until end of turn.", "1/2")
    private val glenRallycaller = Cards.invent("Glen Rallycaller", "{1}{W}", "Creature — Rabbit Warrior", """
        Whenever one or more other Rabbits, Bats, Birds, and/or Mice you control enter, scry 1.
        Other Rabbits, Bats, Birds, and Mice you control get +1/+1.
    """, "2/3")
    private val lancekingfisher = Cards.invent("Lancekingfisher", "{3}{U}{U}", "Creature — Bird Knight", """
        Flying
        Whenever another nontoken Bird you control enters, create a 1/1 blue Fish creature token.
    """, "4/5")
    private val cloudhopPair = Cards.invent("Cloudhop Pair", "{4}{U}", "Creature — Bird Frog", """
        Flying
        When this creature enters, exile up to one other target creature you control. Return it to the battlefield under its owner's control at the beginning of the next end step.
    """, "3/3")
    private val rippleGateway = Cards.invent("Ripple Gateway", "{U}", "Sorcery", "Exile target creature you control, then return it to the battlefield under its owner's control. If that creature is a Bird, Frog, Otter, or Rat, draw a card.")
    private val glenFloodsinger = Cards.invent("Glen Floodsinger", "{2}{U}", "Creature — Otter Wizard", """
        Flash
        You may cast noncreature spells as though they had flash.
        Whenever you cast a noncreature spell, Birds, Frogs, Otters, and Rats you control get +1/+1 until end of turn. Untap them.
    """, "2/2")
    private val glenRotsinger = Cards.invent("Glen Rotsinger", "{1}{B}", "Creature — Squirrel Warlock", """
        Menace
        Whenever this creature attacks, each opponent loses X life and you gain X life, where X is the number of other Squirrels, Bats, Lizards, and Rats you control.
    """, "1/3")
    private val fennickTheCoiledThought = Cards.invent("Fennick, the Coiled Thought", "{3}{B}", "Legendary Creature — Rat Warlock", """
        Whenever Fennick or another Rat you control enters, create a 1/1 black Snail creature token if you don't control a Snail. Otherwise, put a +1/+1 counter on a Snail you control.
        {U}{B}{R}, Sacrifice a Snail: Fennick deals damage equal to the sacrificed creature's power to each opponent. Then draw cards equal to the sacrificed creature's power.
    """, "2/4")
    private val thornwardCaptain = Cards.invent("Thornward Captain", "{3}{R}", "Creature — Mouse Soldier", "At the beginning of combat on your turn, target creature you control gets +X/+0 until end of turn, where X is this creature's power.", "2/3")
    private val glenFlamesinger = Cards.invent("Glen Flamesinger", "{2}{R}", "Creature — Lizard Warlock", "If a Lizard, Mouse, Otter, or Raccoon you control would deal damage to a permanent or player, it deals that much damage plus 1 instead.", "3/3")
    private val glenMightsinger = Cards.invent("Glen Mightsinger", "{G}", "Creature — Frog Warrior", """
        Trample
        Whenever another Frog, Rabbit, Raccoon, or Squirrel you control enters, put a +1/+1 counter on this creature.
    """, "1/1")
    private val maribelHeirToCindercrag = Cards.invent("Maribel, Heir to Cindercrag", "{1}{R}{W}", "Legendary Creature — Mouse Soldier", """
        Other Mice you control get +1/+1.
        When Maribel enters, create Cindercrag, a legendary colorless Equipment artifact token with "Equipped creature gets +1/+1 and has vigilance, trample, and haste" and equip {2}.
    """, "3/3")
    private val plumelineTutor = Cards.invent("Plumeline Tutor", "{1}{W}{U}", "Creature — Bird Scout", """
        Flying
        Whenever this creature or another creature you control with flying enters, put a +1/+1 counter on target creature you control without flying.
    """, "2/3")
    private val seedhuskPage = Cards.invent("Seedhusk Page", "{3}{W/U}", "Creature — Bird Scout", """
        Flying
        Whenever this creature attacks, target creature you control without flying gets +1/+1 until end of turn.
    """, "3/3")
    private val lotuspadHamlet = Cards.invent("Lotuspad Hamlet", null, "Land", """
        {T}: Add {C}.
        {T}: Add {U}. Spend this mana only to cast a creature spell.
        {U}, {T}: Surveil 2. Activate only if a Bird, Frog, Otter, or Rat entered the battlefield under your control this turn.
    """)
    private val lupinbloomHamlet = Cards.invent("Lupinbloom Hamlet", null, "Land", """
        {T}: Add {C}.
        {T}: Add {W}. Spend this mana only to cast a creature spell.
        {1}{W}, {T}, Sacrifice this land: Look at the top six cards of your library. You may reveal a Bat, Bird, Mouse, or Rabbit card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """)
    private val mudbankHamlet = Cards.invent("Mudbank Hamlet", null, "Land", """
        {T}: Add {C}.
        {T}: Add {B}. Spend this mana only to cast a creature spell.
        {1}{B}, {T}, Sacrifice this land: Return target Bat, Lizard, Rat, or Squirrel card from your graveyard to your hand.
    """)
    private val acornhollowHamlet = Cards.invent("Acornhollow Hamlet", null, "Land", """
        {T}: Add {C}.
        {T}: Add {G}. Spend this mana only to cast a creature spell.
        {G}, {T}: Put a +1/+1 counter on each Frog, Rabbit, Raccoon, or Squirrel you control that entered the battlefield this turn.
    """)
    private val cliffsideHamlet = Cards.invent("Cliffside Hamlet", null, "Land", """
        {T}: Add {C}.
        {T}: Add {R}. Spend this mana only to cast a creature spell.
        {R}, {T}: Target Lizard, Mouse, Otter, or Raccoon you control gets +1/+0 and gains haste until end of turn. Activate only as a sorcery.
    """)

    // ---- Chunk 07: life gain and loss

    // CR 611.3a: a static ability with a condition
    @Test
    fun lifesapConduitFliesAfterLosingLife() = scenario {
        alice.battlefield("Swamp", lifesapConduit)
        alice.hand(pact)
        expect { lifesapConduit lacksKeyword Keyword.Flying }
        alice.casts(pact)
        resolveAll()
        expect {
            lifesapConduit hasKeyword Keyword.Flying
            lifesapConduit hasKeyword Keyword.Vigilance
        }
    }

    // CR 603.10a: the dies trigger moves the counters it had
    @Test
    fun lifesapConduitGrowsAndPassesItsCounters() = scenario {
        alice.battlefield("Plains", "Mountain", lifesapConduit, bear)
        alice.hand(heal, shock)
        alice.casts(heal)
        resolveAll()
        expect { lifesapConduit.counters(p1p1) shouldBe 1 }
        alice.casts(shock, target = lifesapConduit)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.graveyard has lifesapConduit
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 603.4: the end step trigger checks the life gained or lost this turn
    @Test
    fun constellationMapperFindsASmallCreature() = scenario {
        alice.battlefield("Plains", constellationMapper)
        alice.hand(heal)
        alice.library(giant, bear, "Island", ponder)
        alice.casts(heal)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun constellationMapperDoesNothingOnAQuietTurn() = scenario {
        alice.battlefield(constellationMapper)
        alice.library(giant, bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            stack.isEmpty()
            alice.hand.isEmpty()
        }
    }

    @Test
    fun quartzknifeKillerDrainsWhenAttacking() = scenario {
        alice.battlefield(quartzknifeKiller)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(quartzknifeKiller)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    @Test
    fun swoopwingPairDrainsOnEntering() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(swoopwingPair)
        alice.casts(swoopwingPair)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun hestersLullabyReturnsTwoAndDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(hestersLullaby)
        alice.graveyard(bear, ogre)
        alice.casts(hestersLullaby, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has ogre
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun lunastoneHeraldArmsTheBats() = scenario {
        alice.battlefield("Plains", lunastoneHerald, bat)
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect {
            bat.power shouldBe 2
            bat hasKeyword Keyword.Deathtouch
            lunastoneHerald.power shouldBe 2
        }
    }

    @Test
    fun ominousObeliskDrainsAtCombat() = scenario {
        alice.battlefield(ominousObelisk)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    @Test
    fun ominousObeliskCashesInForTwoCards() = scenario {
        alice.battlefield(ominousObelisk)
        alice.library(bear, ogre)
        alice.activates(ominousObelisk)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, ogre)
            alice.life shouldBe 18
            alice.graveyard has ominousObelisk
        }
    }

    @Test
    fun starlightDivinerSurveilsAfterALoss() = scenario {
        alice.battlefield("Swamp", starlightDiviner)
        alice.hand(pact)
        alice.library(ogre, bear)
        alice.casts(pact)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    @Test
    fun mindprowlHexerPicksTheDiscardAfterALoss() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp", "Swamp")
        alice.hand(shock, mindprowlHexer)
        bob.hand(ogre, "Forest")
        alice.casts(shock, target = "bob")
        resolveAll()
        alice.casts(mindprowlHexer)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        // the only nonland card is chosen without asking
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.hand.names shouldBe listOf("Forest")
        }
    }

    @Test
    fun mindprowlHexerOtherwiseTheyDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(mindprowlHexer)
        bob.hand(ogre, "Forest")
        alice.casts(mindprowlHexer)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        bob.chooses("Forest")
        resolveAll()
        expect { bob.hand.names shouldBe listOf(ogre) }
    }

    // CR 603.4: an intervening "if" on an enters trigger
    @Test
    fun embercacheNewtAddsManaAfterALoss() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(shock, embercacheNewt)
        alice.casts(shock, target = "bob")
        resolveAll()
        alice.casts(embercacheNewt)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun embercacheNewtRummages() = scenario {
        alice.battlefield("Mountain", "Mountain", embercacheNewt)
        alice.hand(ogre)
        alice.library(bear)
        alice.activates(embercacheNewt, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand has bear
        }
    }

    // CR 602.5b: "only once each turn"
    @Test
    fun paidTalonPingsAndThenGrowsOnce() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", paidTalon, lizard)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lizard)
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(paidTalon)
        resolveAll()
        expect { paidTalon.counters(p1p1) shouldBe 1 }
        assertFailsWith<IllegalArgumentException> { alice.activates(paidTalon) }
    }

    @Test
    fun paidTalonCantGrowBeforeALoss() = scenario {
        alice.battlefield("Mountain", "Mountain", paidTalon)
        assertFailsWith<IllegalArgumentException> { alice.activates(paidTalon) }
    }

    // CR 614.1c: "enters with" is a replacement
    @Test
    fun smolderingThroatslitterEntersBiggerAfterALoss() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shock, smolderingThroatslitter)
        alice.casts(shock, target = "bob")
        resolveAll()
        alice.casts(smolderingThroatslitter)
        resolveAll()
        expect { smolderingThroatslitter.counters(p1p1) shouldBe 1 }
    }

    // CR 505.1a: only the second main phase is a postcombat main phase
    @Test
    fun kilnglassTutorExilesAPlayableCardAfterCombat() = scenario {
        alice.battlefield("Mountain", kilnglassTutor)
        alice.hand(shock)
        alice.library("Forest", bear)
        alice.casts(shock, target = "bob")
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        alice.plays("Forest")
        expect {
            alice.battlefield has "Forest"
            alice.exile has bear
        }
    }

    // CR 614.1c: an extra counter for each opponent who lost life this turn
    @Test
    fun vexxPingsOnALizardSpellAndAddsACounter() = scenario {
        alice.battlefield("Mountain", vexxScorchedScale)
        alice.hand(lizard)
        alice.casts(lizard)
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.life shouldBe 19
            lizard.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun moonlitAssemblyDrainsAndMakesABat() = scenario {
        alice.battlefield("Plains", "Swamp", moonlitAssembly)
        alice.hand(heal, pact)
        alice.casts(heal)
        resolveAll()
        alice.casts(pact)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.battlefield has "Bat Token"
            "Bat Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun moonlitAssemblyDrawsForLife() = scenario {
        alice.battlefield("Swamp", "Swamp", moonlitAssembly)
        alice.library(bear)
        alice.activates(moonlitAssembly)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 18
        }
    }

    @Test
    fun duskrisePriestGainsLifeAttacking() = scenario {
        alice.battlefield(duskrisePriest)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(duskrisePriest)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun skywatchElderPunishesAfterALifeChange() = scenario {
        alice.battlefield("Plains", skywatchElder)
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        advanceTo(TurnStep.End)
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 603.12: "when you do": a reflexive trigger after paying {W}{B} and 2 life; CR 122.1h: finality counter
    @Test
    fun noctaraReturnsAPermanentWithFinality() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(noctaraStarcaller)
        alice.graveyard(bear)
        alice.casts(noctaraStarcaller, paying = listOf("Plains", "Swamp", "Swamp#1"))
        resolveAll()
        may(true)
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.counters(CounterKind.Finality) shouldBe 1
            alice.life shouldBe 18
        }
    }

    @Test
    fun noctaraGainsLifeForAttackingBats() = scenario {
        alice.battlefield(noctaraStarcaller, bat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bat)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    // ---- Chunk 08: typal creatures and lands

    // CR 603.2: "the second time this ability has resolved this turn"
    @Test
    fun reapriteHostessDrawsOnTheSecondRabbit() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(reapriteHostess, rabbit)
        alice.library(bear)
        alice.casts(reapriteHostess)
        resolveAll()
        targetsIfAsked(reapriteHostess)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(rabbit) }
        alice.casts(rabbit)
        resolveAll()
        targets(rabbit)
        resolveAll()
        expect {
            alice.hand has bear
            rabbit.power shouldBe 2
            reapriteHostess.power shouldBe 4
        }
    }

    @Test
    fun magpieRescuerReturnsACheaperCreature() = scenario {
        alice.battlefield("Mountain", magpieRescuer)
        alice.hand(shock)
        alice.graveyard(rabbit, giant)
        alice.casts(shock, target = magpieRescuer)
        resolveAll()
        targetsIfAsked(rabbit)
        resolveAll()
        expect {
            alice.battlefield has rabbit
            alice.graveyard has giant
        }
    }

    @Test
    fun crestedQuartermasterBoostsAGroundCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear, flyer)
        alice.hand(crestedQuartermaster)
        alice.casts(crestedQuartermaster)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    // CR 122.1b: a flying counter
    @Test
    fun deliveranceHeronFlickersWithAFlyingCounter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(deliveranceHeron)
        alice.casts(deliveranceHeron)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun veteranBurrowguardHitsHarderWithAToken() = scenario {
        alice.battlefield("Plains", veteranBurrowguard)
        alice.hand(rabbitCall)
        alice.casts(rabbitCall)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(veteranBurrowguard)
        resolveAll()
        expect { veteranBurrowguard.power shouldBe 3 }
    }

    @Test
    fun veteranBurrowguardWithoutAToken() = scenario {
        alice.battlefield(veteranBurrowguard)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(veteranBurrowguard)
        resolveAll()
        expect { veteranBurrowguard.power shouldBe 1 }
    }

    @Test
    fun glenRallycallerLeadsAndScries() = scenario {
        alice.battlefield("Plains", glenRallycaller, bat, bear)
        alice.hand(rabbit)
        alice.library(ogre, giant)
        expect {
            bat.power shouldBe 2
            bear.power shouldBe 2
        }
        alice.casts(rabbit)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            rabbit.power shouldBe 2
            alice.library.names shouldBe listOf(giant, ogre)
        }
    }

    @Test
    fun lancekingfisherMakesAFishForABird() = scenario {
        alice.battlefield("Plains", lancekingfisher)
        alice.hand(bird)
        alice.casts(bird)
        resolveAll()
        expect { alice.battlefield has "Fish Token" }
    }

    @Test
    fun cloudhopPairFlickersUntilTheEndStep() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(cloudhopPair)
        alice.casts(cloudhopPair)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun rippleGatewayFlickersAFrogAndDraws() = scenario {
        alice.battlefield("Island", frog)
        alice.hand(rippleGateway)
        alice.library(bear)
        alice.casts(rippleGateway, target = frog)
        resolveAll()
        expect {
            alice.battlefield has frog
            alice.hand has bear
        }
    }

    @Test
    fun rippleGatewayDrawsNothingForABear() = scenario {
        alice.battlefield("Island", bear)
        alice.hand(rippleGateway)
        alice.library(ogre)
        alice.casts(rippleGateway, target = bear)
        resolveAll()
        expect { alice.hand.isEmpty() }
    }

    // CR 702.8a, 601.3b: cast as though it had flash
    @Test
    fun glenFloodsingerFlashesSorceriesAndUntaps() = scenario {
        alice.battlefield("Island", glenFloodsinger)
        alice.battlefield(frog, tapped = true)
        alice.hand(ponder)
        alice.library(bear)
        turn(active = bob, priority = alice)
        alice.casts(ponder)
        resolveAll()
        expect {
            frog.tapped shouldBe false
            frog.power shouldBe 2
            glenFloodsinger.power shouldBe 3
            alice.hand has bear
        }
    }

    @Test
    fun glenRotsingerDrainsPerKin() = scenario {
        alice.battlefield(glenRotsinger, bat, rat, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glenRotsinger)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun fennickGrowsASnailAndFlingsIt() = scenario {
        alice.battlefield(fennickTheCoiledThought, "Swamp", "Swamp", "Island", "Swamp", "Mountain")
        alice.hand(rat, rat)
        alice.library(bear, ogre)
        alice.casts(rat, paying = listOf("Swamp"))
        resolveAll()
        expect { alice.battlefield has "Snail Token" }
        alice.casts(rat, paying = listOf("Swamp#1"))
        resolveAll()
        expect { "Snail Token".counters(p1p1) shouldBe 1 }
        alice.activates(fennickTheCoiledThought, choosing = listOf("Snail Token"))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand.names shouldBe listOf(bear, ogre)
        }
    }

    @Test
    fun thornwardCaptainLendsItsPower() = scenario {
        alice.battlefield(thornwardCaptain, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        targets(bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    // CR 614.1a: damage increased by 1
    @Test
    fun glenFlamesingerAddsOneDamage() = scenario {
        alice.battlefield(glenFlamesinger, lizard, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glenFlamesinger, lizard, bear)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 12 }
    }

    @Test
    fun glenMightsingerGrowsForKin() = scenario {
        alice.battlefield("Forest", glenMightsinger)
        alice.hand(frog)
        alice.casts(frog)
        resolveAll()
        expect { glenMightsinger.counters(p1p1) shouldBe 1 }
    }

    // CR 111.4: a named legendary token
    @Test
    fun maribelLeadsTheMiceAndMakesCindercrag() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", "Plains", "Plains", mouse)
        alice.hand(maribelHeirToCindercrag)
        alice.casts(maribelHeirToCindercrag)
        resolveAll()
        expect {
            mouse.power shouldBe 2
            alice.battlefield has "Cindercrag"
        }
        alice.activates("Cindercrag", target = mouse)
        resolveAll()
        expect {
            mouse.power shouldBe 3
            mouse hasKeyword Keyword.Trample
            mouse hasKeyword Keyword.Haste
        }
    }

    @Test
    fun plumelineTutorBoostsAGroundCreature() = scenario {
        alice.battlefield("Plains", "Island", "Island", bear)
        alice.hand(plumelineTutor)
        alice.casts(plumelineTutor)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun seedhuskPagePumpsAGroundCreatureWhenAttacking() = scenario {
        alice.battlefield(seedhuskPage, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(seedhuskPage)
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    // CR 602.5b: activate only after a Bird, Frog, Otter or Rat entered this turn
    @Test
    fun lotuspadHamletSurveilsAfterAnOtter() = scenario {
        alice.battlefield(lotuspadHamlet, "Island", "Island")
        alice.hand(otter)
        alice.library(ogre, bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(lotuspadHamlet, ability = 2) }
        alice.casts(otter, paying = listOf("Island"))
        resolveAll()
        alice.activates(lotuspadHamlet, ability = 2, paying = listOf("Island#1"))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    // CR 106.6: mana spendable only on creature spells
    @Test
    fun lotuspadHamletManaCastsOnlyCreatures() = scenario {
        alice.battlefield(lotuspadHamlet)
        alice.hand(otter)
        alice.activates(lotuspadHamlet, ability = 1)
        alice.casts(otter)
        resolveAll()
        expect { alice.battlefield has otter }
    }

    @Test
    fun lupinbloomHamletFindsARabbit() = scenario {
        alice.battlefield(lupinbloomHamlet, "Plains", "Plains")
        alice.library(bear, rabbit, ogre)
        alice.activates(lupinbloomHamlet, ability = 2)
        resolveAll()
        alice.chooses(rabbit)
        resolveAll()
        expect {
            alice.hand has rabbit
            alice.graveyard has lupinbloomHamlet
        }
    }

    @Test
    fun mudbankHamletReturnsARat() = scenario {
        alice.battlefield(mudbankHamlet, "Swamp", "Swamp")
        alice.graveyard(rat, bear)
        alice.activates(mudbankHamlet, ability = 2, target = rat)
        resolveAll()
        expect { alice.hand has rat }
    }

    // CR 603.6a: "entered the battlefield this turn"
    @Test
    fun acornhollowHamletGrowsNewcomers() = scenario {
        alice.battlefield(acornhollowHamlet, "Forest", "Forest", squirrel)
        alice.hand(frog)
        alice.casts(frog, paying = listOf("Forest"))
        resolveAll()
        alice.activates(acornhollowHamlet, ability = 2, paying = listOf("Forest#1"))
        resolveAll()
        expect {
            frog.counters(p1p1) shouldBe 1
            squirrel.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun cliffsideHamletHastesALizard() = scenario {
        alice.battlefield(cliffsideHamlet, "Mountain")
        alice.battlefield(lizard, sick = true)
        alice.activates(cliffsideHamlet, ability = 2, target = lizard)
        resolveAll()
        expect {
            lizard.power shouldBe 2
            lizard hasKeyword Keyword.Haste
        }
    }
}
