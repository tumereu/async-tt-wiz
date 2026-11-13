package arcanum.engine.sets.rvr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Ravnica Remastered chunk 05 in play: a doubled life total, toughness gained as it last existed, paid life making each other player
 * sacrifice, cards that trigger as they are milled, target cards in the target player's graveyard, a power counting exile and graveyard,
 * creatures and tokens that leave or return at the next end step, a dies trigger for the turn that spares its source, a name check
 * against the creature that entered, a shared permanent type, tap abilities, borrowed loyalty abilities, lands that can't be targeted
 * or played from graveyards, a hand exiled face down, a spell cast from an opponent's hand and a search with a total mana value. */
class RvrChunk05EffectsScenariosTest {
    private val reckoning = Cards.inventFaces("Split",
        face("Restoral", "{W/B}{W/B}", "Sorcery", "Return target creature card with mana value 3 or less from your graveyard to the battlefield.", null, null, null),
        face("Reckoning Due", "{4}{W}{B}", "Sorcery", "Double your life total. Target opponent loses half their life, rounded up.", null, null, null))
    private val verdict = Cards.invent("Verdict of Exile", "{W}", "Instant",
        "Put target attacking creature on the bottom of its owner's library. Its controller gains life equal to its toughness.")
    private val velka = Cards.invent("Velka, Queen of the Rot", "{2}{B}{G}", "Legendary Creature — Elf Shaman", """
        Whenever you sacrifice a black creature, you may pay 2 life. If you do, each other player sacrifices a creature of their choice.
        Whenever you sacrifice a green creature, you may gain 2 life.
    """, "2/2")
    private val amoeba = Cards.invent("Dreamdrift Amoeba", "{1}{U}", "Creature — Illusion", """
        Flying
        When this card is put into your graveyard from your library, you may put it onto the battlefield.
    """, "1/1")
    private val frost = Cards.invent("Seeping Frost", "{3}{B}", "Sorcery", """
        Seeping Frost deals 3 damage to each opponent and you gain 3 life.
        When Seeping Frost is put into your graveyard from your library, you may exile it. If you do, Seeping Frost deals 3 damage to each opponent and you gain 3 life.
    """)
    private val trickle = Cards.invent("Mindrot Trickle", "{U}", "Sorcery", "Target player mills three cards.")
    private val shaman = Cards.invent("Silt-Stirrer Shaman", "{2}{G}", "Creature — Centaur Shaman",
        "When this creature enters, target player shuffles any number of target cards from their graveyard into their library.", "3/2")
    private val drake = Cards.invent("Sparkwing Drake", "{U}{U}{R}{R}", "Creature — Drake", """
        Flying
        Sparkwing Drake's power is equal to the total number of instant and sorcery cards you own in exile and in your graveyard.
        When this creature enters, draw a card.
    """, "*/4")
    private val spiritpath = Cards.invent("Spiritpath", "{2}{W}", "Instant",
        "Exile each creature you control. Return those cards to the battlefield under their owner's control at the beginning of the next end step.")
    private val cultmage = Cards.invent("Cackling Cultmage", "{B/R}{B/R}", "Creature — Zombie Shaman", """
        ({B/R} can be paid with either {B} or {R}.)
        {3}{B}, Discard a card: Target creature gets -2/-2 until end of turn.
        {3}{R}: Create a 2/1 red Goblin creature token with haste. Exile it at the beginning of the next end step.
    """, "2/2")
    private val maiden = Cards.invent("Carnage Maiden", "{3}{B}{B}", "Legendary Creature — Human Assassin", """
        Menace
        When Carnage Maiden enters, each other creature gets -1/-1 until end of turn. Whenever a creature dies this turn, each creature other than Carnage Maiden gets -1/-1 until end of turn.
    """, "4/4")
    private val steward = Cards.invent("Steward Initiative", "{3}{G}", "Enchantment",
        "Whenever a nontoken creature you control enters, if it doesn't have the same name as another creature you control or a creature card in your graveyard, draw a card.")
    private val trinket = Cards.invent("Mistglass Trinket", "{3}", "Artifact",
        "Whenever a nonartifact permanent you control enters, you may return another permanent you control that shares a permanent type with it to its owner's hand.")
    private val whetstone = Cards.invent("Tinkerer's Whetstone", "{2}", "Artifact", "{1}, {T}: Untap target creature that has an activated ability with {T} in its cost.")
    private val vorthrax = Cards.invent("Vorthrax, Dragon-Tyrant", "{U}{B}{B}{B}{R}", "Legendary Planeswalker — Bolas", """
        Vorthrax has all loyalty abilities of all other planeswalkers on the battlefield.
        [+1]: You draw a card. Each opponent exiles a card from their hand or a permanent they control.
        [−3]: Destroy target creature or planeswalker.
        [−8]: Each opponent who doesn't control a legendary creature or planeswalker loses the game.
    """, loyalty = "4")
    private val varnek = Cards.invent("Varnek, Esteemed Barrister", "{W}{W}", "Legendary Creature — Human Advisor", """
        Flying
        Lands on the battlefield and land cards in graveyards can't be the targets of spells or abilities your opponents control.
        Your opponents can't play land cards from graveyards.
    """, "2/3")
    private val sanctum = Cards.invent("Corked Sanctum", "{4}", "Artifact", """
        At the beginning of each opponent's upkeep, exile all cards from your hand face down.
        At the beginning of your upkeep, return all cards you own exiled with this artifact to your hand, then draw a card.
    """)
    private val horror = Cards.invent("Thoughtsap Horror", "{5}{U}{B}{B}", "Creature — Horror", """
        Trample
        Whenever this creature deals combat damage to a player, you may look at that player's hand. If you do, you may cast a spell from among those cards without paying its mana cost.
    """, "6/6")
    private val grummok = Cards.invent("Grummok, the Ruin-Boar", "{3}{R}{R}", "Legendary Creature — Boar God", """
        Trample
        Whenever Grummok attacks, you may put a creature card from your hand onto the battlefield tapped and attacking. Return that creature to your hand at the beginning of the next end step.
        When Grummok dies or is put into exile from the battlefield, you may put it into its owner's library third from the top.
    """, "6/6")
    private val behemoth = Cards.invent("Shifting Behemoth", "{5}{G}{G}", "Creature — Beast",
        "When this creature dies, search your library for any number of creature cards with total mana value 6 or less, put them onto the battlefield, then shuffle.", "6/6")

    private val brute = Cards.invent("Gloomfen Brute", "{2}{B}", "Creature — Ogre", "", "3/3")
    private val hound = Cards.invent("Silt Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val jab = Cards.invent("Searing Jab", "{R}", "Instant", "Searing Jab deals 3 damage to target creature.")
    private val adept = Cards.invent("Tapper Adept", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1/1")
    private val sage = Cards.invent("Stonepath Sage", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 2 life.", loyalty = "3")
    private val reclaimer = Cards.invent("Rubble Reclaimer", "{3}", "Artifact", "You may play lands from your graveyard.")
    private val collapse = Cards.invent("Quarry Collapse", "{2}{R}", "Sorcery", "Destroy target land.")
    private val sweep = Cards.invent("Grave Sweep", "{B}", "Instant", "Exile target card from a graveyard.")
    private val scribe = Cards.invent("Marsh Scribe", "{1}{U}", "Sorcery", "Draw a card.")
    private val edict = Cards.invent("Doom Edict", "{B}", "Instant", "Destroy target creature.")
    private val imp = Cards.invent("Mote Imp", "{B}", "Creature — Imp", "", "1/1")
    private val altar = Cards.invent("Bonepit Altar", "{1}", "Artifact", "Sacrifice a creature: Add {B}.")

    // ---- Life

    /** Alice at 15 goes to 30; Bob at 9 loses 5 and Carol, not targeted, keeps 20. */
    // CR 701.10d: doubling gains life to twice the total; CR 107.1a: half rounded up
    @Test
    fun reckoningDueDoublesYourLifeAndHalvesAnOpponents() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(reckoning)
        alice.life = 15
        bob.life = 9
        alice.casts(reckoning, target = "bob", way = CastWay(face = 1))
        resolveAll()
        expect {
            alice.life shouldBe 30
            bob.life shouldBe 4
            carol.life shouldBe 20
        }
    }

    /** Bob's attacking 3/3 goes to the bottom of his library and Bob gains 3. */
    // CR 608.2h: its toughness as it last existed
    @Test
    fun verdictGivesItsControllerLifeEqualToItsToughness() = scenario {
        alice.battlefield("Plains")
        alice.hand(verdict)
        bob.battlefield(brute)
        bob.library("Island", "Island")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(brute)
        bob.passes()
        alice.casts(verdict, target = brute)
        resolveAll()
        expect {
            bob.life shouldBe 23
            bob.library.size shouldBe 3
            bob.battlefield lacks brute
        }
    }

    /** Alice sacrifices a black creature and pays 2 life: Bob and Carol each sacrifice a creature, Alice keeps hers. */
    // CR 701.21a, 101.4: each other player chooses one of their own
    @Test
    fun velkaMakesEachOtherPlayerSacrifice() = scenario(players = 3) {
        alice.battlefield(velka, altar, brute, hound)
        bob.battlefield(hound)
        carol.battlefield(brute)
        alice.activates(altar, choosing = listOf(brute))
        playOut()
        expect {
            alice.life shouldBe 18
            alice.battlefield has hound
            bob.battlefield lacks hound
            carol.battlefield lacks brute
        }
    }

    // ---- Graveyards and libraries

    /** Milled, the Amoeba comes onto the battlefield from the graveyard; the mill trigger of Seeping Frost exiles it and drains. */
    // CR 603.6d: library-to-graveyard triggers work from the graveyard the card is put into
    @Test
    fun milledCardsTriggerFromTheGraveyard() = scenario(players = 3) {
        alice.battlefield("Island")
        alice.hand(trickle)
        alice.library(amoeba, frost, "Plains", "Plains")
        alice.casts(trickle, target = "alice")
        playOut()
        expect {
            alice.battlefield has amoeba
            alice.exile has frost
            bob.life shouldBe 17
            carol.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    /** Bob shuffles two chosen cards of his graveyard back; a card in Carol's graveyard can't be chosen with them. */
    // CR 115.1: the cards target in the target player's graveyard
    @Test
    fun siltStirrerShufflesTargetCardsOfTheTargetPlayer() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(shaman)
        bob.graveyard(brute, hound, scribe)
        carol.graveyard(jab)
        alice.casts(shaman)
        allPass()
        resolveAll()
        answer(Answer.Targets(listOf(target("bob"))))
        val d = decision<Decision.Targets>()
        assertTrue(d.options.none { (it as? arcanum.engine.state.TargetRef.Object)?.id == card(jab, zone = Zone.Graveyard) })
        answer(Answer.Targets(listOf(target(brute), target(hound))))
        playOut()
        expect {
            bob.graveyard.size shouldBe 1
            bob.library.size shouldBe 2
            carol.graveyard.size shouldBe 1
        }
    }

    /** Two instants and sorceries in exile and one in the graveyard make it 3/4; a creature card and Bob's sorcery don't count. */
    // CR 604.3, 108.3: cards you own in exile and in your graveyard
    @Test
    fun sparkwingDrakeCountsExileAndGraveyard() = scenario {
        alice.battlefield(drake)
        alice.exile(jab, trickle)
        alice.graveyard(scribe, brute)
        bob.graveyard(collapse)
        expect { drake.power shouldBe 3 }
    }

    // ---- Next end step

    /** Alice's creatures leave and come back at the end step as new, untapped objects. */
    // CR 603.7, 400.7
    @Test
    fun spiritpathReturnsTheCreaturesAtTheEndStep() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", brute, hound)
        alice.hand(spiritpath)
        bob.battlefield(hound)
        alice.casts(spiritpath)
        resolveAll()
        expect {
            alice.exile has brute
            alice.exile has hound
            bob.battlefield has hound
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has brute
            alice.battlefield has hound
        }
    }

    /** The Goblin token is exiled at the next end step. */
    // CR 603.7c, 111.7
    @Test
    fun cultmageTokenIsExiledAtTheEndStep() = scenario {
        alice.battlefield(cultmage, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(cultmage, ability = 1)
        resolveAll()
        expect { assertTrue(alice.battlefield.names.contains("Goblin Token")) }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { assertTrue(!alice.battlefield.names.contains("Goblin Token")) }
    }

    /** A creature dying this turn gives every other creature -1/-1, chaining, while Carnage Maiden stays 4/4. */
    // CR 603.7, 603.7d: the delayed trigger's source is Carnage Maiden
    @Test
    fun carnageMaidenSparesItself() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(maiden)
        bob.battlefield(hound, brute)
        alice.battlefield(imp)
        alice.casts(maiden)
        resolveAll()
        expect {
            maiden.power shouldBe 4
            maiden.toughness shouldBe 4
            bob.battlefield lacks hound
            bob.battlefield lacks brute
        }
    }

    // ---- Names, types and abilities

    /** The first Silt Hound draws a card; a second one doesn't, nor does a Brute with a Brute card in the graveyard. */
    // CR 603.4, 201.2
    @Test
    fun stewardDrawsOnlyForNewNames() = scenario {
        alice.battlefield(steward, "Forest", "Forest", "Swamp", "Swamp", "Swamp", "Forest", "Forest")
        alice.hand(hound, hound, brute)
        alice.graveyard(brute)
        alice.library("Plains", "Plains", "Plains")
        alice.casts(hound, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        alice.casts(hound, paying = listOf("Forest#2", "Forest#3"))
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
        alice.casts(brute, paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    /** A creature entering may return another creature but not a land or itself. */
    // CR 110.4a, 608.2d
    @Test
    fun trinketReturnsAPermanentSharingAType() = scenario {
        alice.battlefield(trinket, brute, "Forest", "Forest")
        alice.hand(hound)
        alice.casts(hound)
        resolveAll()
        val d = decision<Decision.ChooseObjects>()
        expect { d.options.map { state.obj(it)?.let { o -> o.definition.faces[0].name } } shouldBe listOf(brute) }
        answer(Answer.Objects(d.options))
        resolveAll()
        expect {
            alice.hand has brute
            alice.battlefield has hound
        }
    }

    /** The mana creature can be untapped; a vanilla creature can't be targeted. */
    // CR 107.5, 605.1a
    @Test
    fun whetstoneUntapsOnlyCreaturesWithTapAbilities() = scenario {
        alice.battlefield(whetstone, "Plains", "Plains")
        alice.battlefield(adept, brute, tapped = true)
        alice.activates(whetstone)
        kotlin.test.assertEquals(listOf(target(adept)), decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target(adept))))
        playOut()
        expect { adept.tapped shouldBe false }
    }

    /** Vorthrax uses the other planeswalker's +1; its own +1 makes each opponent exile a card of their own. */
    // CR 613.1f, 606.2; CR 606.3: one loyalty ability of a planeswalker a turn
    @Test
    fun vorthraxBorrowsLoyaltyAbilitiesAndEachOpponentExiles() = scenario(players = 3) {
        alice.battlefield(vorthrax, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield(sage, counters = listOf(CounterKind.Loyalty to 3))
        bob.hand(scribe)
        carol.battlefield(brute)
        alice.library("Plains", "Plains")
        alice.activates(vorthrax, ability = 3)
        resolveAll()
        expect {
            alice.life shouldBe 22
            vorthrax.loyalty shouldBe 5
        }
    }

    /** Its own +1: Bob exiles the card in his hand, Carol her only permanent. */
    // CR 101.4: each opponent chooses their own
    @Test
    fun vorthraxPlusOne() = scenario(players = 3) {
        alice.battlefield(vorthrax, counters = listOf(CounterKind.Loyalty to 4))
        bob.hand(scribe)
        carol.battlefield(brute)
        alice.library("Plains")
        alice.activates(vorthrax, ability = 0)
        playOut(scribe, brute)
        expect {
            alice.hand.size shouldBe 1
            bob.exile has scribe
            carol.exile has brute
        }
    }

    /** At −8 Bob without a legendary creature or planeswalker loses; Carol with a planeswalker stays in. */
    // CR 104.3e
    @Test
    fun vorthraxMinusEight() = scenario(players = 3) {
        alice.battlefield(vorthrax, counters = listOf(CounterKind.Loyalty to 8))
        bob.battlefield(brute)
        carol.battlefield(sage, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(vorthrax, ability = 2)
        resolveAll()
        expect {
            state.player(bob.id).lost shouldBe true
            state.player(carol.id).lost shouldBe false
        }
    }

    /** Bob can't target his own land or a land card in a graveyard, nor play a land from his graveyard; other cards he can. */
    // CR 115.1, 109.5: the opponents of Varnek's controller, whoever controls the land; CR 305.1, 101.2
    @Test
    fun varnekProtectsLandsFromOpponents() = scenario {
        alice.battlefield(varnek, "Mountain")
        alice.graveyard("Forest", brute)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Swamp", reclaimer)
        bob.hand(collapse, sweep)
        bob.graveyard("Plains")
        turn(number = 2, active = bob)
        // no land is a legal target for Bob, his own Swamp included
        assertFailsWith<IllegalArgumentException> { bob.casts(collapse) }
        assertFailsWith<IllegalArgumentException> { bob.plays("Plains") }
        bob.casts(sweep)
        kotlin.test.assertEquals(listOf(target(brute)), decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target(brute))))
        expect { stack.size shouldBe 1 }
    }

    /** Varnek's controller still targets an opponent's land and plays a land from her graveyard. */
    // CR 115.1, 305.1
    @Test
    fun varnekLetsItsControllerTargetAndPlayLands() = scenario {
        alice.battlefield(varnek, "Mountain", "Mountain", "Mountain", reclaimer)
        alice.hand(collapse)
        alice.graveyard("Forest")
        bob.battlefield("Swamp")
        alice.casts(collapse, target = "Swamp")
        resolveAll()
        alice.plays("Forest")
        expect {
            bob.battlefield lacks "Swamp"
            alice.battlefield has "Forest"
        }
    }

    // ---- Hands, exile and libraries

    /** In Bob's upkeep Alice's hand is exiled face down; in her next upkeep it comes back and she draws a card. */
    // CR 607.2a, 406.3
    @Test
    fun sanctumExilesYourHandFaceDownAndReturnsIt() = scenario {
        alice.battlefield(sanctum)
        alice.hand(brute, hound)
        alice.library("Plains", "Plains", "Plains")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.exile.size shouldBe 2
            state.obj(card(brute, zone = Zone.Exile))?.faceDown shouldBe true
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.hand has brute
            alice.hand has hound
            alice.hand.size shouldBe 3
        }
    }

    /** Alice casts the sorcery from Bob's hand for free; it goes to Bob's graveyard. */
    // CR 601.2, 608.2g; CR 400.3: to its owner's graveyard
    @Test
    fun horrorCastsASpellFromTheDamagedPlayersHand() = scenario {
        alice.battlefield(horror)
        alice.library("Plains", "Plains")
        bob.hand(scribe, "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(horror)
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        playOut(scribe)
        expect {
            bob.life shouldBe 14
            bob.graveyard has scribe
            bob.hand has "Island"
            alice.hand.size shouldBe 1
        }
    }

    /** The creature put onto the battlefield attacking returns to Alice's hand at the end step. */
    // CR 508.4, 603.7, 400.7
    @Test
    fun grummokPutsACreatureInAttackingAndReturnsIt() = scenario {
        alice.battlefield(grummok)
        alice.hand(brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grummok)
        playOut(brute)
        expect {
            alice.battlefield has brute
            brute.tapped shouldBe true
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has brute }
    }

    /** Exiled from the battlefield, Grummok goes third from the top of Alice's library (and so doesn't return with the others). */
    // CR 401.1; CR 400.7: it is a new object in the library
    @Test
    fun grummokGoesThirdFromTheTopWhenExiled() = scenario {
        alice.battlefield(grummok, "Plains", "Plains", "Plains")
        alice.hand(spiritpath)
        alice.library("Island", "Swamp", "Forest")
        alice.casts(spiritpath)
        playOut()
        expect { alice.library.names shouldBe listOf("Island", "Swamp", grummok, "Forest") }
    }

    /** Dying, it finds three creature cards with total mana value 6; a pick totalling 7 is refused. */
    // CR 701.23a, 202.3
    @Test
    fun behemothFindsCreaturesWithTotalManaValueSixOrLess() = scenario {
        alice.battlefield(behemoth, "Swamp")
        alice.hand(edict)
        alice.library(maiden, brute, hound, adept, "Plains")
        alice.casts(edict, target = behemoth)
        resolveAll()
        decision<Decision.ChooseObjects>()
        assertFailsWith<IllegalArgumentException> { answer(Answer.Objects(listOf(card(maiden, zone = Zone.Library), card(hound, zone = Zone.Library)))) }
        playOut(brute, hound, adept)
        expect {
            alice.battlefield has brute
            alice.battlefield has hound
            alice.battlefield has adept
            alice.library has maiden
        }
    }
}
