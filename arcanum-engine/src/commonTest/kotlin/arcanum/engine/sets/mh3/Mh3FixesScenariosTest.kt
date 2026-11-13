package arcanum.engine.sets.mh3

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.spm.loreChapter
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.cards
import arcanum.engine.setup.inOrder
import arcanum.engine.setup.keep
import arcanum.engine.setup.startGame
import arcanum.engine.setup.table
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rules fixes found while working MH3: Aura spell target legality, what a creature put onto the battlefield attacking attacks,
 * "Search your library and/or graveyard" and "draw cards equal to its power. If you do, discard that many cards" (the invented
 * names carry the oracle text of The Hunger Tide Rises and Horrid Shadowspinner).
 */
class Mh3FixesScenariosTest {
    private val shackle = Cards.invent("Gloomwrap Shackle", "{1}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -2/-0.
    """)
    private val veil = Cards.invent("Veilcloak Gesture", "{U}", "Instant", "Target creature you control gains hexproof until end of turn.")
    private val runt = Cards.invent("Tidecaller Runt", "{1}{U}", "Creature — Merfolk", "", "2/2")

    // CR 608.3b, 702.11b: an Aura spell whose creature gained hexproof in response doesn't resolve; it goes to its owner's graveyard
    @Test
    fun auraSpellWhoseTargetGainedHexproofDoesNotResolve() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(shackle)
        bob.battlefield(runt, "Island")
        bob.hand(veil)
        alice.casts(shackle, target = runt)
        alice.passes()
        bob.casts(veil, target = runt)
        resolveAll()
        expect {
            alice.graveyard has shackle
            card(runt).power shouldBe 2
        }
    }

    private val herald = Cards.invent("Brinkside Herald", "{2}{W}", "Creature — Human Soldier", """
        Whenever this creature attacks, create a 1/1 white Soldier creature token that's tapped and attacking.
    """, "2/2")
    private val recruiter = Cards.invent("Brinkside Recruiter", "{2}{R}", "Creature — Human Warrior", """
        Whenever this creature attacks, you may put a creature card from your hand onto the battlefield tapped and attacking.
    """, "2/2")

    // CR 508.4: a token created tapped and attacking attacks the player its controller chooses, not the first opponent (3 players)
    @Test
    fun tokenEnteringAttackingAttacksTheChosenPlayer() = scenario(players = 3) {
        alice.battlefield(herald)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(herald, defender = "bob")
        resolveAll()
        val d = decision<Decision.Targets>()
        assertEquals(listOf(target("bob"), target("carol")), d.options)
        targets("carol")
        resolveAll()
        val token = state.combat!!.attackers.single { it.id != card(herald) }
        assertEquals(TargetRef.Player(carol.id), token.target)
    }

    // CR 508.4: a creature card put onto the battlefield tapped and attacking attacks the player its controller chooses (3 players)
    @Test
    fun creatureCardPutOntoTheBattlefieldAttackingAttacksTheChosenPlayer() = scenario(players = 3) {
        alice.battlefield(recruiter)
        alice.hand(runt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(recruiter, defender = "bob")
        resolveAll()
        alice.chooses(runt)
        targets("carol")
        resolveAll()
        val attacker = state.combat!!.attackers.single { it.id == card(runt) }
        assertEquals(TargetRef.Player(carol.id), attacker.target)
    }

    private val tide = Cards.invent("The Swarm Tide Swells", "{2}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III — Create a 1/1 black and green Insect creature token.
        IV — Sacrifice any number of creatures. Search your library and/or graveyard for a creature card with mana value less than or equal to the number of creatures sacrificed this way and put it onto the battlefield. If you search your library this way, shuffle.
    """)

    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val shade = Cards.invent("Gloomhollow Shade", "{B}{B}", "Creature — Shade", "", "2/2")
    private val titan = Cards.invent("Hollow Titan", "{7}", "Creature — Eldrazi", "", "7/7")

    // CR 701.23a, 701.24a: "library and/or graveyard": the searcher chooses; searching only the graveyard leaves the library unshuffled
    @Test
    fun searchingOnlyTheGraveyardDoesNotShuffle() = scenario(seed = 3) {
        alice.battlefield(bear, shade)
        alice.graveyard(runt)
        loreChapter(tide, 4, "Island", titan, "Plains", "Swamp", "Mountain", "Forest", "Plains", "Swamp", "Mountain", "Forest")
        val before = state.player(alice.id).library
        resolveAll()
        alice.chooses(bear, shade)
        resolveAll()
        val zones = decision<Decision.ChooseOption>()
        assertEquals(listOf("library", "graveyard", "library and graveyard"), zones.options)
        answer(Answer.Option("graveyard"))
        if (pending is Decision.ChooseObjects) alice.chooses(runt)
        resolveAll()
        assertEquals(before, state.player(alice.id).library)
        expect {
            alice.battlefield has runt
        }
    }

    private val webspinner = Cards.invent("Gloomy Webspinner", "{1}{U}{B}", "Creature — Horror", """
        Lifelink
        Whenever this creature attacks, you may draw cards equal to its power. If you do, discard that many cards.
    """, "2/3")
    private val brand = Cards.invent("Brand, King of the Vale", "{4}{W}{U}", "Legendary Creature — Human Noble Archer", """
        Reach, vigilance
        If you would draw a card except the first one you draw in each of your draw steps, draw two cards instead.
        If one or more tokens would be created under your control, twice that many of those tokens are created instead.
    """, "3/5")

    // CR 121.2, 121.2a, 616.1g: "that many" is the number the draw named (its power, 2), not the four cards drawn with each draw doubled
    @Test
    fun webspinnerDiscardsItsPowerEvenWhenDrawsAreReplaced() = scenario {
        alice.battlefield(webspinner, brand)
        alice.library("Plains", "Island", "Swamp", "Mountain", "Forest", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(webspinner)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(2, d.min)
        assertEquals(2, d.max)
        alice.chooses("Plains", "Island")
        resolveAll()
        expect {
            alice.hand.names.size shouldBe 2
            alice.graveyard.names.size shouldBe 2
        }
    }

    private val lantern = Cards.invent("Hoarfrost Lantern", "{2}", "Artifact", "Players can't untap more than one nonbasic land during their untap steps.")
    private val ruins = Cards.invent("Rimecrest Ruins", null, "Land", "{T}: Add {C}.")
    private val sinkhole = Cards.invent("Frostvale Sinkhole", null, "Land", "{T}: Add {C}.")

    // CR 502.3: with an untap limit the active player chooses which nonbasic land untaps; basic lands and the rest untap as usual
    @Test
    fun untapLimitLetsTheActivePlayerChooseWhichUntaps() = scenario(players = 3) {
        bob.battlefield(lantern)
        alice.battlefield(ruins, sinkhole, "Forest", runt, tapped = true)
        alice.library("Plains", "Plains")
        turn(number = 3, active = carol, step = TurnStep.End)
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, d.player)
        assertEquals(setOf(card(ruins), card(sinkhole)), d.options.toSet())
        assertEquals(1, d.min)
        assertEquals(1, d.max)
        alice.chooses(sinkhole)
        expect {
            card(sinkhole).tapped shouldBe false
            card(ruins).tapped shouldBe true
            card("Forest").tapped shouldBe false
            card(runt).tapped shouldBe false
        }
    }

    private val krovath = Cards.invent("Krovath, Scion of Rot", "{4}{B/P}{B/P}{B/P}", "Legendary Creature — Phyrexian Horror Minion", """
        ({B/P} can be paid with either {B} or 2 life.)
        Lifelink
        For each {B} in a cost, you may pay 2 life rather than pay that mana.
        Whenever you cast a black spell, put a +1/+1 counter on this creature.
    """, "2/2")
    private val hedge = Cards.invent("Briarhook Hedge", "{B/G}", "Creature — Plant", "", "1/1")

    // CR 601.2h, 119.4: with one Swamp, the other {B} of the cost is paid with 2 life; the cast trigger still counts it as a black spell
    @Test
    fun payLifeRatherThanBlackMana() = scenario {
        alice.battlefield(krovath, "Swamp")
        alice.hand(shade)
        alice.casts(shade)
        resolveAll()
        expect {
            alice.battlefield has shade
            alice.life shouldBe 18
            card(krovath).power shouldBe 3
        }
    }

    // CR 601.2b, 107.4e: a hybrid {B/G} symbol is announced as {B} before it's paid, so it may be paid with 2 life
    @Test
    fun payLifeRatherThanBlackManaPaysAHybridSymbolAnnouncedAsBlack() = scenario {
        alice.battlefield(krovath)
        alice.hand(hedge)
        alice.casts(hedge)
        resolveAll()
        expect {
            alice.battlefield has hedge
            alice.life shouldBe 18
        }
    }

    private val widow = Cards.invent("Gravesap Widow", "{2/B}{2/B}", "Creature — Spider", "", "2/2")

    // CR 601.2b, 107.4e: each monocolored hybrid {2/B} announced as {B} may be paid with 2 life
    @Test
    fun payLifeRatherThanBlackManaPaysMonocoloredHybrid() = scenario {
        alice.battlefield(krovath)
        alice.hand(widow)
        alice.casts(widow)
        resolveAll()
        expect {
            alice.battlefield has widow
            alice.life shouldBe 16
        }
    }

    // CR 107.4e: a hybrid symbol without a black half ({G/W}) still needs mana
    @Test
    fun payLifeRatherThanBlackManaLeavesNonBlackHybridAlone() = scenario {
        alice.battlefield(krovath)
        alice.hand(sprout)
        alice.casts(sprout)
        assertEquals(emptyList(), decision<Decision.ManaPayment>().suggestion)
    }
    private val sprout = Cards.invent("Dawnmeadow Sprout", "{G/W}", "Creature — Plant", "", "1/1")

    private val maw = Cards.invent("Maw of the Foretold End", "{5}{C}{C}", "Creature — Eldrazi", """
        You may reveal this card from your opening hand. If you do, at the beginning of your first upkeep, look at the top four cards of your library. You may put one of those cards back on top of your library. Exile the rest.
        When you cast this spell, exile target permanent that's one or more colors.
    """, "6/6")

    // CR 103.6b, 603.7: revealed from the opening hand, it sets up a trigger at its owner's first upkeep: one of the top four back, three exiled
    @Test
    fun revealedFromTheOpeningHandLooksAtTheFirstUpkeep() = scenario(start = table(cards(listOf(maw), "Swamp", "Elvish Seer"),
        cards(emptyList(), "Mountain", "Elvish Seer"))) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        val offer = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, offer.player)
        assertEquals(listOf(card(maw)), offer.options)
        answer(Answer.Objects(offer.options))
        resolveAll()
        val keepOne = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(keepOne.options.take(1)))
        resolveAll()
        expect {
            alice.exile.names.size shouldBe 3
            alice.library.names.size shouldBe 30
            alice.hand has maw
        }
    }

    // CR 103.6b: "you may": not revealed, nothing happens at the first upkeep
    @Test
    fun notRevealedFromTheOpeningHandDoesNothing() = scenario(start = table(cards(listOf(maw), "Swamp", "Elvish Seer"),
        cards(emptyList(), "Mountain", "Elvish Seer"))) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        answer(Answer.Objects(emptyList()))
        resolveAll()
        expect {
            alice.exile.names.size shouldBe 0
            alice.library.names.size shouldBe 33
        }
    }

    private val goyf = Cards.invent("Gravemaw Lhurgoyf", "{B}", "Creature — Lhurgoyf", """
        This creature's power is equal to the number of card types among cards in your graveyard and its toughness is equal to that number plus 1.
        Escape—{2}{B}, Exile any number of other cards from your graveyard with four or more card types among them. (You may cast this card from your graveyard for its escape cost.)
    """, "*/1+*")
    private val grub = Cards.invent("Clockwork Grub", "{2}", "Artifact Creature — Construct", "", "1/1")

    // CR 702.138a, 205.2a: escape's cost: three other cards with four card types among them (land, instant, artifact, creature)
    @Test
    fun escapeExilesCardsWithFourCardTypesAmongThem() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(goyf, "Island", veil, grub, runt)
        alice.casts(goyf, choosing = listOf("Island", veil, grub))
        resolveAll()
        expect {
            alice.battlefield has goyf
            alice.exile has grub
            alice.graveyard has runt
        }
    }

    // CR 205.2a, 601.2h: cards with only three card types among them (land, instant, creature) don't pay it
    @Test
    fun escapeRefusesFewerThanFourCardTypes() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(goyf, "Island", veil, runt, grub)
        kotlin.test.assertFails { alice.casts(goyf, choosing = listOf("Island", veil, runt)) }
    }

    private val sentinel = Cards.invent("Gravecall Sentinel", "{2}{B}", "Legendary Creature — Demon Cleric", """
        Flying
        Whenever another nontoken creature you control dies, put a +1/+1 counter on this creature. If that creature was a Cleric, you may draw a card. If you do, you lose 1 life.
    """, "2/2")
    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")

    // CR 603.10a: "that creature" is the one that died (a Merfolk), not this Cleric: a counter, but no draw is offered
    @Test
    fun thatCreatureInADiesTriggerIsTheCreatureThatDied() = scenario {
        alice.battlefield(sentinel, runt, "Mountain")
        alice.hand(cinder)
        alice.library("Plains", "Plains")
        alice.casts(cinder, target = runt)
        resolveAll()
        assertEquals(null, pending)
        expect {
            card(sentinel).power shouldBe 3
            alice.hand.names.size shouldBe 0
            alice.life shouldBe 20
        }
    }

    private val vestment = Cards.invent("Vestment of the Chapel", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and is a Cleric in addition to its other types.
        Equip {1}
    """)

    // CR 603.10a, 608.2h: "If that creature was a Cleric" looks at the creature as it last existed on the battlefield: a Merfolk that was a
    // Cleric through Equipment offers the draw though the card in the graveyard isn't a Cleric
    @Test
    fun thatCreatureWasAClericUsesLastKnownInformation() = scenario {
        alice.battlefield(sentinel, runt, vestment, "Mountain")
        attach(vestment, to = runt)
        alice.hand(cinder)
        alice.library("Plains", "Plains")
        alice.casts(cinder, target = runt)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            card(sentinel).power shouldBe 3
            alice.hand.names.size shouldBe 1
            alice.life shouldBe 19
        }
    }
}
