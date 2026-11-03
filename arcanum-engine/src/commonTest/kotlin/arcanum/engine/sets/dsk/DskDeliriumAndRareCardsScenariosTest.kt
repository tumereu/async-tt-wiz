package arcanum.engine.sets.dsk

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dsk.Dsk.bear
import arcanum.engine.sets.dsk.Dsk.giant
import arcanum.engine.sets.dsk.Dsk.ogre
import arcanum.engine.sets.dsk.Dsk.p1p1
import arcanum.engine.sets.dsk.Dsk.shock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Duskmourn's delirium cards, Overseers, Lasting creatures and Ley Lines in play (parser chunks 07–08), beyond the mechanics played in [DskMechanicsScenariosTest], [DskGapsScenariosTest] and [DskOpeningHandScenariosTest]. */
class DskDeliriumAndRareCardsScenariosTest {
    private val infernalCounsel = Cards.invent("Infernal Counsel", "{1}{B}", "Sorcery", """
        Search your library for a Demon card, reveal it, put it into your hand, then shuffle.
        Delirium — If there are four or more card types among cards in your graveyard, instead search your library for any card, put it into your hand, then shuffle.
    """)
    private val shallWePlay = Cards.invent("Shall We Play", "{3}{B}", "Sorcery", """
        Delirium — Choose one. If there are four or more card types among cards in your graveyard, choose one or more instead.
        • Creatures your opponents control get -1/-1 until end of turn.
        • Each opponent discards two cards.
        • Each opponent loses 3 life and you gain 3 life.
    """)
    private val bonyTwistscare = Cards.invent("Bony Twistscare", "{1}{B}", "Artifact Creature — Scarecrow", """
        Lifelink
        Delirium — At the beginning of your end step, if there are four or more card types among cards in your graveyard, each opponent may sacrifice a nonland permanent of their choice or discard a card. Then this creature deals damage equal to its power to each opponent who didn't sacrifice a permanent or discard a card this way.
    """, "2/2")
    private val risenCultist = Cards.invent("Risen Cultist", "{2}{B}", "Creature — Human Cleric", "Delirium — {2}{B}{B}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if there are four or more card types among cards in your graveyard and only as a sorcery. (If a creature with a finality counter on it would die, exile it instead.)", "4/1")
    private val dreadOfBurning = Cards.invent("Dread of Burning", "{4}{R}{R}", "Enchantment Creature — Nightmare", """
        When this creature enters, it deals 4 damage to each opponent.
        Delirium — Whenever a source you control deals noncombat damage to an opponent, if there are four or more card types among cards in your graveyard, this creature deals that amount of damage to target creature that player controls.
    """, "4/4")
    private val dreadOfBeingLeftOut = Cards.invent("Dread of Being Left Out", "{1}{R}", "Enchantment Creature — Nightmare", """
        When this creature enters, discard a card, then draw a card.
        Delirium — Whenever this creature attacks for the first time each turn, if there are four or more card types among cards in your graveyard, untap target creature. After this phase, there is an additional combat phase.
    """, "2/3")
    private val feedingHand = Cards.invent("Feeding Hand", "{1}{R}", "Creature — Mutant", "Delirium — Whenever this creature attacks while there are four or more card types among cards in your graveyard, it gets +2/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)", "2/2")
    private val inexplicableInferno = Cards.invent("Inexplicable Inferno", "{4}{R}", "Instant", """
        Inexplicable Inferno deals 6 damage to target creature.
        Delirium — If there are four or more card types among cards in your graveyard, exile the top card of your library. You may play it until the end of your next turn.
    """)
    private val theCoasterOfCarnage = Cards.invent("The Coaster of Carnage", "{X}{2}{R}", "Legendary Enchantment", """
        Delirium — If a source you control would deal noncombat damage to a permanent or player while there are four or more card types among cards in your graveyard, it deals double that damage instead.
        When The Coaster of Carnage enters, it deals X damage to each of up to X target creatures.
    """)
    private val savageUrge = Cards.invent("Savage Urge", "{R}", "Instant", """
        Target creature gets +1/+0 and gains first strike until end of turn.
        Delirium — If there are four or more card types among cards in your graveyard, that creature gains double strike until end of turn.
    """)
    private val banisterWurm = Cards.invent("Banister Wurm", "{3}{G}{G}", "Creature — Wurm", """
        This spell can't be countered.
        Trample, haste
        Delirium — {2}{G}{G}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if there are four or more card types among cards in your graveyard and only as a sorcery.
    """, "5/5")
    private val ravenousFlytrap = Cards.invent("Ravenous Flytrap", "{2}{G}", "Creature — Plant", "Delirium — Whenever this creature enters or attacks, if there are four or more card types among cards in your graveyard, distribute two +1/+1 counters among one or two target creatures. Then if there are six or more card types among cards in your graveyard, double the number of +1/+1 counters on those creatures.", "2/4")
    private val stitchworkBeastie = Cards.invent("Stitchwork Beastie", "{G}", "Artifact Creature — Beast", """
        Delirium — This creature can't attack or block unless there are four or more card types among cards in your graveyard.
        At the beginning of your upkeep, you may mill a card. (You may put the top card of your library into your graveyard.)
    """, "3/3")
    private val spinebiterCentipede = Cards.invent("Spinebiter Centipede", "{2}{G}", "Creature — Insect", """
        When this creature enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        Delirium — This creature gets +1/+2 and has vigilance as long as there are four or more card types among cards in your graveyard.
    """, "2/1")
    private val wickerworkReaper = Cards.invent("Wickerwork Reaper", "{3}{G}", "Artifact Creature — Scarecrow", "Delirium — Whenever this creature attacks, if there are four or more card types among cards in your graveyard, look at the top card of your library. If it's a land card, you may put it onto the battlefield. If you don't put the card onto the battlefield, put it into your hand.", "5/4")
    private val beastieBrawl = Cards.invent("Beastie Brawl", "{R}{G}", "Sorcery", """
        Choose target creature you control and target creature an opponent controls.
        Delirium — If there are four or more card types among cards in your graveyard, put two +1/+1 counters on the creature you control.
        The creature you control deals damage equal to its power to the creature an opponent controls.
    """)
    private val pullToTheRoots = Cards.invent("Pull to the Roots", "{2}{B}{G}", "Instant", """
        Delirium — This spell costs {2} less to cast as long as there are four or more card types among cards in your graveyard.
        Destroy target nonland permanent.
    """)
    private val theSwarmspinner = Cards.invent("The Swarmspinner", "{2}{B}{G}", "Legendary Artifact Creature — Scarecrow", """
        When The Swarmspinner enters, create two 1/1 black and green Insect creature tokens with flying.
        Delirium — As long as there are four or more card types among cards in your graveyard, Insects and Spiders you control get +1/+1 and have deathtouch.
    """, "2/3")
    private val kindledWickerfolk = Cards.invent("Kindled Wickerfolk", "{R}{G}", "Artifact Creature — Scarecrow", """
        Haste
        Delirium — This creature gets +1/+1 and has trample as long as there are four or more card types among cards in your graveyard.
    """, "3/2")
    private val wynneMisanthropicWarden = Cards.invent("Wynne, Misanthropic Warden", "{1}{B}{R}{G}", "Legendary Creature — Human Warlock", """
        Ward {2}
        At the beginning of your upkeep, each player draws two cards.
        Delirium — As long as there are four or more card types among cards in your graveyard, each opponent's maximum hand size is equal to seven minus the number of those card types.
    """, "3/4")
    private val peekPastTheVeil = Cards.invent("Peek Past the Veil", "{2}{R}{G}", "Instant", "Discard your hand. Then draw X cards, where X is the number of card types among cards in your graveyard.")
    private val broodweaver = Cards.invent("Broodweaver", "{B}{G}", "Creature — Spider", """
        Reach
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        {4}{B}{G}, {T}, Sacrifice this creature: Create a number of 1/1 black and green Insect creature tokens with flying equal to the number of card types among cards in your graveyard.
    """, "2/3")
    private val overseerOfTheMistmoors = Cards.invent("Overseer of the Mistmoors", "{5}{W}{W}", "Enchantment Creature — Avatar Horror", """
        Impending 4—{2}{W}{W} (If you cast this spell for its impending cost, it enters with four time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)
        Whenever this permanent enters or attacks, create two 2/1 white Insect creature tokens with flying.
    """, "6/6")
    private val overseerOfTheFloodpits = Cards.invent("Overseer of the Floodpits", "{3}{U}{U}", "Enchantment Creature — Avatar Horror", """
        Impending 4—{1}{U}{U} (If you cast this spell for its impending cost, it enters with four time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)
        Flying
        Whenever this permanent enters or attacks, draw two cards, then discard a card.
    """, "5/3")
    private val overseerOfTheBalemurk = Cards.invent("Overseer of the Balemurk", "{3}{B}{B}", "Enchantment Creature — Avatar Horror", """
        Impending 5—{1}{B} (If you cast this spell for its impending cost, it enters with five time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)
        Whenever this permanent enters or attacks, mill four cards, then you may return a non-Avatar creature card or a planeswalker card from your graveyard to your hand.
    """, "5/5")
    private val overseerOfTheBoilerbilges = Cards.invent("Overseer of the Boilerbilges", "{4}{R}{R}", "Enchantment Creature — Avatar Horror", """
        Impending 4—{2}{R}{R} (If you cast this spell for its impending cost, it enters with four time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)
        Whenever this permanent enters or attacks, it deals 4 damage to any target.
    """, "5/5")
    private val overseerOfTheHauntwoods = Cards.invent("Overseer of the Hauntwoods", "{3}{G}{G}", "Enchantment Creature — Avatar Horror", """
        Impending 4—{1}{G}{G} (If you cast this spell for its impending cost, it enters with four time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)
        Whenever this permanent enters or attacks, create a tapped colorless land token named Everywhere that is every basic land type.
    """, "6/5")
    private val lastingInnocence = Cards.invent("Lasting Innocence", "{1}{W}{W}", "Enchantment Creature — Sheep Glimmer", """
        Lifelink
        Whenever one or more other creatures you control with power 2 or less enter, draw a card. This ability triggers only once each turn.
        When Lasting Innocence dies, if it was a creature, return it to the battlefield under its owner's control. It's an enchantment. (It's not a creature.)
    """, "2/1")
    private val lastingTenacity = Cards.invent("Lasting Tenacity", "{2}{B}{B}", "Enchantment Creature — Snake Glimmer", """
        Whenever you gain life, target opponent loses that much life.
        When Lasting Tenacity dies, if it was a creature, return it to the battlefield under its owner's control. It's an enchantment. (It's not a creature.)
    """, "4/3")
    private val lastingCourage = Cards.invent("Lasting Courage", "{2}{R}{R}", "Enchantment Creature — Dog Glimmer", """
        Whenever another creature you control enters, it gets +2/+0 and gains haste until end of turn.
        When Lasting Courage dies, if it was a creature, return it to the battlefield under its owner's control. It's an enchantment. (It's not a creature.)
    """, "3/3")
    private val lastingVitality = Cards.invent("Lasting Vitality", "{1}{G}{G}", "Enchantment Creature — Elk Glimmer", """
        Vigilance
        Creatures you control have "{T}: Add one mana of any color."
        When Lasting Vitality dies, if it was a creature, return it to the battlefield under its owner's control. It's an enchantment. (It's not a creature.)
    """, "3/3")
    private val leyLineOfHope = Cards.invent("Ley Line of Hope", "{2}{W}{W}", "Enchantment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        If you would gain life, you gain that much life plus 1 instead.
        As long as you have at least 7 life more than your starting life total, creatures you control get +2/+2.
    """)
    private val leyLineOfTransformation = Cards.invent("Ley Line of Transformation", "{2}{U}{U}", "Enchantment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        As this enchantment enters, choose a creature type.
        Creatures you control are the chosen type in addition to their other types. The same is true for creature spells you control and creature cards you own that aren't on the battlefield.
    """)
    private val leyLineOfTheVoid = Cards.invent("Ley Line of the Void", "{2}{B}{B}", "Enchantment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        If a card would be put into an opponent's graveyard from anywhere, exile it instead.
    """)
    private val leyLineOfResonance = Cards.invent("Ley Line of Resonance", "{2}{R}{R}", "Enchantment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        Whenever you cast an instant or sorcery spell that targets only a single creature you control, copy that spell. You may choose new targets for the copy.
    """)
    private val leyLineOfMutation = Cards.invent("Ley Line of Mutation", "{2}{G}{G}", "Enchantment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        You may pay {W}{U}{B}{R}{G} rather than pay the mana cost for spells you cast.
    """)
    private val behemoth = Cards.invent("Hallway Behemoth", "{7}{G}{G}", "Creature — Beast", "", "9/9")

    /** Four card types for delirium (CR 207.2c): a land, a creature, a sorcery and an artifact. */
    private val delirium = arrayOf("Swamp", Dsk.human, Dsk.ponder, Dsk.idol)

    // ---- Chunk 07: delirium (the ability word itself: DskMechanicsScenariosTest)

    @Test
    fun infernalCounselFindsADemon() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(infernalCounsel)
        alice.library(bear, Dsk.demon)
        alice.casts(infernalCounsel)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(Dsk.demon)
        resolveAll()
        expect { alice.hand has Dsk.demon }
    }

    @Test
    fun infernalCounselFindsAnyCardWithDelirium() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(infernalCounsel)
        alice.graveyard(*delirium)
        alice.library(bear, Dsk.demon)
        alice.casts(infernalCounsel)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    // CR 700.2d: "choose one or more instead"
    @Test
    fun shallWePlayChoosesSeveralWithDelirium() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shallWePlay)
        alice.graveyard(*delirium)
        bob.battlefield(Dsk.flyer)
        alice.casts(shallWePlay, modes = listOf(0, 2))
        resolveAll()
        expect {
            Dsk.flyer.power shouldBe 1
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun shallWePlayChoosesOneWithout() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shallWePlay)
        bob.hand(bear, ogre)
        alice.casts(shallWePlay)
        val d = decision<Decision.Modes>()
        assertEquals(1, d.max)
        answer(Answer.Modes(listOf(1)))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect { bob.hand.isEmpty() }
    }

    @Test
    fun bonyTwistscareHitsAnOpponentWhoGivesNothing() = scenario {
        alice.battlefield(bonyTwistscare)
        alice.graveyard(*delirium)
        advanceTo(TurnStep.End)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 602.1: an ability that works from the graveyard; CR 122.1h: finality counter
    @Test
    fun risenCultistReturnsWithFinality() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(risenCultist, *delirium)
        alice.activates(risenCultist)
        resolveAll()
        expect {
            alice.battlefield has risenCultist
            risenCultist.counters(CounterKind.Finality) shouldBe 1
        }
    }

    @Test
    fun dreadOfBurningRedirectsWithDelirium() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dreadOfBurning)
        alice.graveyard(*delirium)
        bob.battlefield(ogre)
        alice.casts(dreadOfBurning)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            bob.life shouldBe 16
            bob.graveyard has ogre
        }
    }

    @Test
    fun dreadOfBeingLeftOutLoots() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(dreadOfBeingLeftOut, giant)
        alice.library(bear)
        alice.casts(dreadOfBeingLeftOut)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect {
            alice.graveyard has giant
            alice.hand has bear
        }
    }

    // CR 500.8: an additional combat phase
    @Test
    fun dreadOfBeingLeftOutAddsACombat() = scenario {
        alice.battlefield(dreadOfBeingLeftOut, bear)
        alice.graveyard(*delirium)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dreadOfBeingLeftOut)
        targetsIfAsked(dreadOfBeingLeftOut)
        resolveAll()
        expect { dreadOfBeingLeftOut.tapped shouldBe false }
        advanceTo(TurnStep.EndOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dreadOfBeingLeftOut)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun feedingHandMenacesWithDelirium() = scenario {
        alice.battlefield(feedingHand)
        alice.graveyard(*delirium)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(feedingHand)
        resolveAll()
        expect {
            feedingHand.power shouldBe 4
            feedingHand hasKeyword Keyword.Menace
        }
    }

    @Test
    fun inexplicableInfernoExilesAPlayableCardWithDelirium() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(inexplicableInferno)
        alice.graveyard(*delirium)
        alice.library("Forest")
        bob.battlefield(giant)
        alice.casts(inexplicableInferno, target = giant)
        resolveAll()
        alice.plays("Forest")
        expect {
            bob.graveyard has giant
            alice.battlefield has "Forest"
        }
    }

    // CR 614.1a: damage doubled
    @Test
    fun theCoasterOfCarnageDoublesItsDamageWithDelirium() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(theCoasterOfCarnage)
        alice.graveyard(*delirium)
        bob.battlefield(bear)
        alice.casts(theCoasterOfCarnage, x = 1)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun theCoasterOfCarnageWithoutDelirium() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(theCoasterOfCarnage)
        bob.battlefield(bear)
        alice.casts(theCoasterOfCarnage, x = 1)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { card(bear).view.obj?.damage shouldBe 1 }
    }

    @Test
    fun savageUrgeGivesDoubleStrikeWithDelirium() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(savageUrge)
        alice.graveyard(*delirium)
        alice.casts(savageUrge, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun banisterWurmReturnsWithDelirium() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.graveyard(banisterWurm, *delirium)
        alice.activates(banisterWurm)
        resolveAll()
        expect {
            alice.battlefield has banisterWurm
            banisterWurm.counters(CounterKind.Finality) shouldBe 1
        }
    }

    // CR 601.2d: distributing counters
    @Test
    fun ravenousFlytrapDistributesCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(ravenousFlytrap)
        alice.graveyard(*delirium)
        alice.casts(ravenousFlytrap)
        resolveAll()
        targets(bear)
        if (pending is Decision.Divide) divideAsSuggested()
        resolveAll()
        assertEquals(null, pending)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun stitchworkBeastieCantAttackWithoutDelirium() = scenario {
        alice.battlefield(stitchworkBeastie)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(stitchworkBeastie) }
    }

    @Test
    fun stitchworkBeastieAttacksWithDeliriumAndMills() = scenario {
        alice.battlefield(stitchworkBeastie)
        alice.graveyard(*delirium)
        alice.library(bear, ogre)
        bob.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stitchworkBeastie)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.graveyard has bear
        }
    }

    @Test
    fun spinebiterCentipedeFetchesAndGrowsWithDelirium() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(spinebiterCentipede)
        alice.library(bear, "Plains")
        alice.casts(spinebiterCentipede)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            alice.hand has "Plains"
            spinebiterCentipede.power shouldBe 2
        }
    }

    @Test
    fun spinebiterCentipedeWithDelirium() = scenario {
        alice.battlefield(spinebiterCentipede)
        alice.graveyard(*delirium)
        expect {
            spinebiterCentipede.power shouldBe 3
            spinebiterCentipede.toughness shouldBe 3
            spinebiterCentipede hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun wickerworkReaperPutsALandIn() = scenario {
        alice.battlefield(wickerworkReaper)
        alice.graveyard(*delirium)
        alice.library("Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wickerworkReaper)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.battlefield has "Plains" }
    }

    @Test
    fun beastieBrawlAddsCountersAndBites() = scenario {
        alice.battlefield("Mountain", "Forest", bear)
        alice.hand(beastieBrawl)
        alice.graveyard(*delirium)
        bob.battlefield(ogre)
        alice.casts(beastieBrawl, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            bob.graveyard has ogre
        }
    }

    @Test
    fun pullToTheRootsIsCheaperWithDelirium() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(pullToTheRoots)
        alice.graveyard(*delirium)
        bob.battlefield(Dsk.charm)
        alice.casts(pullToTheRoots, target = Dsk.charm)
        resolveAll()
        expect { bob.graveyard has Dsk.charm }
    }

    @Test
    fun theSwarmspinnerMakesInsectsThatGrowWithDelirium() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest")
        alice.hand(theSwarmspinner)
        alice.casts(theSwarmspinner)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Insect Token" } shouldBe 2
            "Insect Token".power shouldBe 1
        }
    }

    @Test
    fun theSwarmspinnerInsectsWithDelirium() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", "Island")
        alice.hand(theSwarmspinner, Dsk.ponder)
        alice.graveyard("Swamp", Dsk.human, Dsk.idol)
        alice.library(bear)
        alice.casts(theSwarmspinner)
        resolveAll()
        alice.casts(Dsk.ponder)
        resolveAll()
        expect {
            "Insect Token".power shouldBe 2
            "Insect Token" hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun kindledWickerfolkWithDelirium() = scenario {
        alice.battlefield(kindledWickerfolk)
        alice.graveyard(*delirium)
        expect {
            kindledWickerfolk.power shouldBe 4
            kindledWickerfolk hasKeyword Keyword.Trample
        }
    }

    @Test
    fun wynneMakesEveryoneDrawTwo() = scenario {
        alice.battlefield(wynneMisanthropicWarden)
        alice.library(bear, ogre, giant)
        bob.library(bear, ogre, giant)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.hand.size shouldBe 3
        }
    }

    @Test
    fun peekPastTheVeilDrawsByCardTypes() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand(peekPastTheVeil, giant)
        alice.graveyard("Swamp", Dsk.ponder)
        alice.library(bear, ogre, Dsk.human, Dsk.flyer)
        alice.casts(peekPastTheVeil)
        resolveAll()
        expect {
            alice.graveyard has giant
            alice.hand.names shouldBe listOf(bear, ogre, Dsk.human)
        }
    }

    @Test
    fun broodweaverSurveilsOnEntering() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(broodweaver)
        alice.library(ogre, bear)
        alice.casts(broodweaver)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    @Test
    fun broodweaverMakesInsectsByCardTypes() = scenario {
        alice.battlefield(broodweaver, "Swamp", "Swamp", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard("Swamp", Dsk.ponder)
        alice.activates(broodweaver)
        resolveAll()
        // a land, a sorcery and the sacrificed creature card: three card types
        expect { alice.battlefield.names.count { it == "Insect Token" } shouldBe 3 }
    }

    // ---- Chunk 08: Overseers (impending), Lasting (enduring) and Ley Lines

    // CR 702.176a: impending
    @Test
    fun overseerOfTheMistmoorsMakesInsects() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(overseerOfTheMistmoors)
        alice.casts(overseerOfTheMistmoors, way = ownAlternative(overseerOfTheMistmoors))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Insect Token" } shouldBe 2
            overseerOfTheMistmoors.counters(CounterKind.Time) shouldBe 4
        }
    }

    @Test
    fun overseerOfTheFloodpitsLoots() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(overseerOfTheFloodpits, giant)
        alice.library(bear, ogre)
        alice.casts(overseerOfTheFloodpits, way = ownAlternative(overseerOfTheFloodpits))
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(bear, ogre) }
    }

    @Test
    fun overseerOfTheBalemurkMillsAndReturns() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(overseerOfTheBalemurk)
        alice.library(bear, "Island", ogre, Dsk.ponder, giant)
        alice.casts(overseerOfTheBalemurk, way = ownAlternative(overseerOfTheBalemurk))
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.library.names shouldBe listOf(giant)
        }
    }

    @Test
    fun overseerOfTheBoilerbilgesBurns() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(overseerOfTheBoilerbilges)
        alice.casts(overseerOfTheBoilerbilges, way = ownAlternative(overseerOfTheBoilerbilges))
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 111.4, 305.6: a named land token with every basic land type
    @Test
    fun overseerOfTheHauntwoodsMakesEverywhere() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(overseerOfTheHauntwoods)
        alice.casts(overseerOfTheHauntwoods, way = ownAlternative(overseerOfTheHauntwoods))
        resolveAll()
        expect {
            alice.battlefield has "Everywhere"
            "Everywhere".tapped shouldBe true
            "Everywhere".subtypes.size shouldBe 5
        }
    }

    @Test
    fun lastingInnocenceDrawsForASmallArrival() = scenario {
        alice.battlefield("Plains", lastingInnocence)
        alice.hand(Dsk.human)
        alice.library(bear)
        alice.casts(Dsk.human)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun lastingTenacityDrainsOnLifeGain() = scenario {
        alice.battlefield("Plains", lastingTenacity)
        alice.hand(Dsk.heal)
        alice.casts(Dsk.heal)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun lastingCourageHastesArrivals() = scenario {
        alice.battlefield("Forest", "Forest", lastingCourage)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Haste
        }
    }

    @Test
    fun lastingVitalityGivesCreaturesManaAbilities() = scenario {
        alice.battlefield(lastingVitality, bear)
        alice.activates(bear, color = Color.Blue)
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 614.1a: gaining 1 more
    @Test
    fun leyLineOfHopeAddsOneLife() = scenario {
        alice.battlefield("Plains", leyLineOfHope, bear)
        alice.hand(Dsk.heal)
        alice.casts(Dsk.heal)
        resolveAll()
        expect {
            alice.life shouldBe 23
            bear.power shouldBe 2
        }
    }

    @Test
    fun leyLineOfHopePumpsAtSevenAbove() = scenario {
        alice.battlefield(leyLineOfHope, bear)
        alice.life = 27
        expect { bear.power shouldBe 4 }
    }

    @Test
    fun leyLineOfTransformationAddsTheChosenType() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(leyLineOfTransformation)
        alice.casts(leyLineOfTransformation)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Elf")))
        expect { bear.subtypes.map { it.name }.contains("Elf") shouldBe true }
    }

    // CR 614.1a: an opponent's card is exiled instead
    @Test
    fun leyLineOfTheVoidExilesTheirCards() = scenario {
        alice.battlefield("Mountain", leyLineOfTheVoid)
        alice.hand(shock)
        bob.battlefield(bear)
        alice.casts(shock, target = bear)
        resolveAll()
        expect {
            bob.exile has bear
            alice.graveyard has shock
        }
    }

    // CR 707.10: a copy of the spell
    @Test
    fun leyLineOfResonanceCopiesASpellOnYourCreature() = scenario {
        alice.battlefield("Forest", leyLineOfResonance, bear)
        alice.hand(Dsk.pump)
        alice.casts(Dsk.pump, target = bear)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    // CR 118.9: an alternative cost another permanent offers
    @Test
    fun leyLineOfMutationCastsForFiveColors() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", leyLineOfMutation)
        alice.hand(behemoth)
        val way = grantedWay(behemoth)
        kotlin.test.assertNotNull(way)
        alice.casts(behemoth, way = way)
        resolveAll()
        expect { alice.battlefield has behemoth }
    }
}
