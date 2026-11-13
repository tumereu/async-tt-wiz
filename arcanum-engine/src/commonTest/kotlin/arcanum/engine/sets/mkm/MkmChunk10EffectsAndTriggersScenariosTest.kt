package arcanum.engine.sets.mkm

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
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.choosePlayer
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM chunk 10: stun counters on only some targets (CR 122.1b), cards put onto the battlefield from graveyards and hands (CR 400.7, 303.4f),
 * face-down returns turned face up (CR 708.8), look-and-reveal and piles (CR 701.20), sacrifices and searches (CR 701.21, 701.23),
 * "attacked this combat" with a block requirement (CR 506.4, 509.1c), last known toughness (CR 603.10a), cards drawn this turn, attack
 * batches of any player (CR 508.1), a reflexive trigger reading the discarded cards (CR 603.12) and solving a Case (CR 719.3). */
class MkmChunk10EffectsAndTriggersScenariosTest {
    private val labyrinth = Cards.invent("Snarled Labyrinth", "{X}{U}{U}", "Enchantment", """
        Flash
        When this enchantment enters, tap X target creatures. Put a stun counter on each of those creatures you don't control.
        Tapped creatures you control have hexproof.
    """.trimIndent())
    private val shoveHaul = Cards.inventFaces("Split",
        face("Shove", "{1}{W/B}", "Sorcery", "Destroy target tapped creature.", null, null, null),
        face("Haul", "{4}{B/R}{B/R}", "Sorcery", "Put up to two target creature cards from a single graveyard onto the battlefield under your control. " +
            "They gain haste until end of turn. Sacrifice them at the beginning of the next end step.", null, null, null))
    private val ooze = Cards.invent("Ooze Against Order", "{2}{G}", "Sorcery", """
        Create a 0/0 green Ooze creature token with trample. Put X +1/+1 counters on it, where X is two plus the total number of cards you own in exile and in your graveyard that are Oozes or are named Ooze Against Order.
        A deck can have any number of cards named Ooze Against Order.
    """.trimIndent())
    private val oozePup = Cards.invent("Bog Ooze Pup", "{G}", "Creature — Ooze", "", "1/1")
    private val fury = Cards.invent("Earthblood Fury", "{X}{R}{G}", "Sorcery",
        "Earthblood Fury deals X damage to any target. Put up to X land cards from your hand and/or graveyard onto the battlefield tapped.")
    private val masque = Cards.invent("Unlawful Masque", "{3}{B}", "Enchantment", """
        Flash
        When this enchantment enters, put an impostor counter on each creature you control.
        Whenever a creature you control with an impostor counter on it dies, exile it. Return up to one other target creature card from your graveyard to the battlefield.
    """.trimIndent())
    private val sniffer = Cards.invent("Lodestone Sniffer", "{5}", "Artifact Creature — Construct", """
        When this creature enters, return target Equipment card from your graveyard to the battlefield attached to this creature.
        Whenever you sacrifice an artifact, put a +1/+1 counter on this creature.
    """.trimIndent(), "4/4")
    private val clasp = Cards.invent("Copper Clasp Gear", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
    private val bygone = Cards.invent("Revisit the Bygone", "{5}{G}{W}", "Sorcery",
        "Return up to one target artifact card, up to one target land card, and up to one target non-Aura enchantment card from your graveyard to the " +
            "battlefield. They are 5/5 Elemental creatures in addition to their other types.")
    private val varuk = Cards.invent("Varuk, Howl of the Elders", "{2}{R}{G}", "Legendary Creature — Centaur Druid", """
        Other creatures you control have haste.
        Whenever one or more face-down creatures you control deal combat damage to a player, draw a card.
        Whenever a face-down creature you control dies, return it to the battlefield face down under its owner's control if it's a permanent card, then turn it face up.
    """.trimIndent(), "4/4")
    private val burst = Cards.invent("Burst Loose", "{R}{G}", "Sorcery",
        "Look at the top six cards of your library. You may reveal a creature card from among them. If that card has mana value 2 or less, you may put " +
            "it onto the battlefield and it gains haste until end of turn. If you didn't put the revealed card onto the battlefield this way, put it into " +
            "your hand. Put the rest on the bottom of your library in a random order.")
    private val pry = Cards.invent("Pry into the Thoughts", "{3}{U}{U}", "Instant",
        "Reveal the top five cards of your library and separate them into two piles. An opponent chooses one of those piles. Put that pile into your " +
            "hand and the other into your graveyard. Create a 0/0 colorless Thopter artifact creature token with flying, then put a +1/+1 counter on it " +
            "for each card put into your graveyard this way.")

    private val thopterwright = Cards.invent("Frantic Thopterwright", "{3}{R}", "Creature — Human Artificer",
        "At the beginning of combat on your turn, create a 1/1 colorless Thopter artifact creature token with flying. It gains haste until end of turn. " +
            "Sacrifice it at the beginning of your next end step.", "2/3")
    private val zarkos = Cards.invent("Zarkos, Lord of Disorder", "{4}{B}{R}", "Legendary Creature — Demon", """
        Flying, trample
        At the beginning of your end step, target opponent may sacrifice two nonland, nontoken permanents of their choice. If they don't, you draw two cards.
    """.trimIndent(), "6/6")
    private val quartermaster = Cards.invent("Bureau Quartermaster", "{4}{U}{U}", "Creature — Sphinx Detective", """
        Flying
        When this creature enters, you may search your graveyard, hand and/or library for a card named Lens of Clues and/or a card named Sleuth's Cap and put them onto the battlefield. If you search your library this way, shuffle.
    """.trimIndent(), "4/3")
    private val lens = Cards.invent("Lens of Clues", "{1}", "Artifact", "")
    private val cap = Cards.invent("Sleuth's Cap", "{2}", "Artifact", "")
    private val ravsimir = Cards.invent("Ravsimir, Dusk's Lantern", "{2}{G}{W}{W}", "Legendary Creature — Elf Scout", """
        Lifelink
        When Ravsimir enters, create Vorna Shadowpaw, a legendary 5/5 green and white Wolf creature token with trample.
        Whenever a Wolf you control attacks, if Ravsimir attacked this combat, target creature an opponent controls blocks that Wolf this combat if able.
    """.trimIndent(), "3/2")
    private val wolf = Cards.invent("Duskhollow Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val lass = Cards.invent("Carnage Lass, Known Slayer", "{2}{B}{B}", "Legendary Creature — Human Assassin", """
        Menace
        Creatures you control have wither.
        Whenever a creature an opponent controls dies, if its toughness was less than 1, draw a card.
    """.trimIndent(), "4/4")
    private val recall = Cards.invent("Quill's Perfect Recall", "{1}{U}", "Legendary Enchantment", """
        When Quill's Perfect Recall enters, draw a card.
        You have no maximum hand size.
        At the beginning of combat on your turn, if you've drawn more than one card this turn, put X +1/+1 counters on target creature you control, where X is the number of cards you've drawn this turn minus one.
    """.trimIndent())
    private val aurelix = Cards.invent("Aurelix, the Code Above", "{3}{R}{W}", "Legendary Creature — Angel", """
        Flying, vigilance, haste
        Whenever a player attacks with three or more creatures, you draw a card.
        Whenever a player attacks with five or more creatures, Aurelix deals 3 damage to each of your opponents and you gain 3 life.
    """.trimIndent(), "4/4")
    private val tormik = Cards.invent("Tormik, Bearer of Law", "{1}{W}{B}", "Legendary Creature — Human Advisor", """
        Affinity for planeswalkers
        Flying, vigilance
        Whenever an opponent attacks with creatures, if two or more of those creatures are attacking you and/or planeswalkers you control, that opponent loses 3 life and you draw a card.
    """.trimIndent(), "2/4")
    private val blast = Cards.invent("Untimely Blast", "{2}{U}{R}", "Sorcery",
        "Draw two cards. Then you may discard two cards. When you do, Untimely Blast deals X damage to each creature, where X is the greatest mana value " +
            "among cards discarded this way.")
    private val examiner = Cards.invent("Dossier Examiner", "{2}{W}", "Creature — Human Detective", """
        When this creature enters and whenever you solve a Case, look at the top six cards of your library. You may reveal an enchantment card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        You may spend mana as though it were mana of any color to cast Case spells.
    """.trimIndent(), "1/4")
    private val idleCase = Cards.invent("Case of the Idle Clerk", "{W}", "Enchantment — Case", """
        To solve — You control no suspected Skeletons.
        Solved — Creatures you control get +1/+0.
    """.trimIndent())
    private val pursuer = Cards.invent("Spiteful Pursuer", "{1}{R}", "Creature — Human Detective",
        "Whenever an opponent sacrifices an artifact, this creature deals 2 damage to them.", "2/2")
    private val bauble = Cards.invent("Brass Spent Bauble", "{1}", "Artifact", "{T}, Sacrifice this artifact: You gain 1 life.")

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 122.1b, 611.2c: both targets are tapped, only the one Alice doesn't control gets a stun counter; her tapped creature has hexproof
    @Test
    fun snarledLabyrinthStunsOnlyTheirTargets() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", Plain.elf)
        alice.hand(labyrinth)
        bob.battlefield(Plain.bear)
        alice.casts(labyrinth, x = 2)
        playOut(Plain.elf, Plain.bear)
        expect {
            Plain.elf.tapped shouldBe true
            Plain.bear.tapped shouldBe true
            Plain.bear.counters(CounterKind.Stun) shouldBe 1
            Plain.elf.counters(CounterKind.Stun) shouldBe 0
            Plain.elf hasKeyword Keyword.Hexproof.kind
            Plain.bear lacksKeyword Keyword.Hexproof.kind
        }
    }

    // CR 115.1, 603.7: two creature cards from Bob's graveyard come in under Alice's control with haste and are sacrificed at the end step
    @Test
    fun haulBorrowsCreaturesFromOneGraveyardUntilTheEndStep() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shoveHaul)
        alice.library("Plains")
        bob.graveyard(Plain.bear, Plain.ogre)
        bob.library("Plains")
        alice.casts(shoveHaul, targets = listOf(Plain.bear, Plain.ogre), way = CastWay(face = 1))
        playOut()
        expect {
            Plain.bear.controller shouldBe alice.id
            Plain.ogre.controller shouldBe alice.id
            Plain.bear hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        playOut()
        expect {
            bob.graveyard has Plain.bear
            bob.graveyard has Plain.ogre
        }
    }

    // CR 115.1: "from a single graveyard": targets from two graveyards are refused
    @Test
    fun haulTargetsOnlyOneGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shoveHaul)
        alice.graveyard(Plain.giant)
        bob.graveyard(Plain.bear)
        assertFailsWith<IllegalArgumentException> { alice.casts(shoveHaul, targets = listOf(Plain.bear, Plain.giant), way = CastWay(face = 1)) }
    }

    // CR 107.3c: X is 2 plus the Ooze Alice owns in exile and the copy in her graveyard; Bob's Ooze doesn't count
    @Test
    fun oozeAgainstOrderCountsExileAndGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(ooze)
        alice.graveyard(ooze)
        alice.exile(oozePup)
        bob.graveyard(oozePup)
        alice.casts(ooze)
        playOut()
        expect {
            card("Ooze Token").counters(Plain.p1p1) shouldBe 4
            card("Ooze Token").power shouldBe 4
        }
    }

    // CR 305.4: lands from the hand and the graveyard, up to X, put onto the battlefield tapped
    @Test
    fun earthbloodFuryPutsLandsFromHandAndGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand(fury, "Island")
        alice.graveyard("Swamp", "Plains")
        alice.casts(fury, target = "bob", x = 2)
        resolveAll()
        assertEquals(2, decision<Decision.ChooseObjects>().max)
        alice.chooses("Island", "Swamp")
        playOut()
        expect {
            bob.life shouldBe 18
            "Island".zone shouldBe Zone.Battlefield
            "Island".tapped shouldBe true
            "Swamp".zone shouldBe Zone.Battlefield
            alice.graveyard has "Plains"
        }
    }

    // CR 603.10a: the dying creature is exiled and isn't a legal "other" target; another creature card returns
    @Test
    fun unlawfulMasqueTradesTheImpostorForAnotherCreature() = scenario {
        alice.battlefield("Swamp", masque)
        alice.battlefield(Plain.bear, counters = listOf(CounterKind.Named("impostor") to 1))
        alice.hand(Plain.doom)
        alice.graveyard(Plain.ogre)
        alice.casts(Plain.doom, target = Plain.bear)
        resolveAll()
        val options = decision<Decision.Targets>().options
        assertFalse(target(Plain.bear) in options)
        assertTrue(target(Plain.ogre) in options)
        playOut(Plain.ogre)
        expect {
            alice.exile has Plain.bear
            alice.battlefield has Plain.ogre
        }
    }

    // CR 303.4f, 301.5c: the Equipment enters attached to the creature
    @Test
    fun lodestoneSnifferReturnsEquipmentAttached() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(sniffer)
        alice.graveyard(clasp)
        alice.casts(sniffer)
        playOut(clasp)
        expect {
            alice.battlefield has clasp
            sniffer.power shouldBe 5
        }
    }

    // CR 613.1d, 613.4b: each returned card is a 5/5 Elemental creature in addition to its types
    @Test
    fun revisitTheBygoneAnimatesWhatItReturns() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Forest", "Forest")
        alice.hand(bygone)
        alice.graveyard(Plain.idol, "Island", Plain.charm)
        alice.casts(bygone, targets = listOf(Plain.idol, "Island", Plain.charm))
        playOut()
        expect {
            Plain.idol.power shouldBe 5
            "Island".toughness shouldBe 5
            Plain.charm.types.contains(CardType.Creature) shouldBe true
            "Island".types.contains(CardType.Land) shouldBe true
            Plain.charm.subtypes.contains(Subtype("Elemental")) shouldBe true
        }
    }

    // CR 708.2a, 708.8: the face-down 2/2 dies, returns face down and is turned face up: a face-up 3/3 Ogre
    @Test
    fun varukReturnsAFaceDownCreatureFaceUp() = scenario {
        alice.battlefield("Mountain", varuk)
        alice.battlefield(Plain.ogre, faceDown = true)
        alice.hand(Plain.shock)
        alice.casts(Plain.shock, target = Plain.ogre)
        playOut()
        expect {
            alice.battlefield has Plain.ogre
            Plain.ogre.power shouldBe 3
            Plain.ogre.view.obj?.faceDown shouldBe false
        }
    }

    // CR 708.2a: a face-down instant card isn't a permanent card: it stays in the graveyard
    @Test
    fun varukLeavesANonpermanentCardInTheGraveyard() = scenario {
        alice.battlefield("Mountain", varuk)
        alice.battlefield(Plain.study, faceDown = true)
        alice.hand(Plain.shock)
        alice.casts(Plain.shock, target = Plain.study)
        playOut()
        expect { alice.graveyard has Plain.study }
    }

    // CR 701.20a: a revealed creature with mana value 2 or less may enter with haste; the rest go to the bottom
    @Test
    fun burstLoosePutsACheapCreatureOntoTheBattlefield() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(burst)
        alice.library(Plain.elf, "Plains", "Island", "Swamp", "Mountain", "Forest", Plain.giant)
        alice.casts(burst)
        playOut(Plain.elf, may = true)
        expect {
            alice.battlefield has Plain.elf
            Plain.elf hasKeyword Keyword.Haste.kind
            alice.library.names.first() shouldBe Plain.giant
        }
    }

    // CR 701.20a: declining to put it onto the battlefield, the revealed card goes to the hand
    @Test
    fun burstLooseOtherwisePutsTheRevealedCardIntoTheHand() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(burst)
        alice.library(Plain.elf, "Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.casts(burst)
        playOut(Plain.elf, may = false)
        expect { alice.hand has Plain.elf }
    }

    // CR 701.20a: a creature with mana value 5 goes to the hand
    @Test
    fun burstLoosePutsAnExpensiveCreatureIntoTheHand() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(burst)
        alice.library(Plain.giant, "Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.casts(burst)
        playOut(Plain.giant, may = true)
        expect {
            alice.hand has Plain.giant
            alice.battlefield lacks Plain.giant
        }
    }

    // Piles: Alice separates, the opponent she chooses (Carol) picks; the Thopter gets a counter for each card put into the graveyard
    @Test
    fun pryIntoTheThoughtsSplitsIntoPilesAnOpponentChooses() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(pry)
        alice.library(Plain.elf, Plain.bear, Plain.ogre, Plain.giant, Plain.dragon, "Plains")
        alice.casts(pry)
        resolveAll()
        choosePlayer(carol)
        alice.chooses(Plain.elf, Plain.bear)
        assertEquals(carol.id, (pending as? Decision.ChooseOption)?.player)
        answer(Answer.Option("second"))
        playOut()
        expect {
            alice.hand has Plain.ogre
            alice.hand has Plain.giant
            alice.hand has Plain.dragon
            alice.graveyard has Plain.elf
            alice.graveyard has Plain.bear
            card("Thopter Token").power shouldBe 2
        }
    }

    // CR 603.7: "your next end step" is this turn's: the Thopter made at the beginning of combat is sacrificed at Alice's end step
    @Test
    fun franticThopterwrightSacrificesTheThopterAtThisEndStep() = scenario {
        alice.battlefield(thopterwright)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        assertEquals(1, tokens("Thopter Token").size)
        expect { card("Thopter Token") hasKeyword Keyword.Haste.kind }
        advanceTo(TurnStep.End)
        playOut()
        assertEquals(0, tokens("Thopter Token").size)
    }

    // CR 118.12: the target opponent (Carol of three players) sacrifices two nonland, nontoken permanents: Alice draws nothing
    @Test
    fun zarkosTargetOpponentMaySacrificeTwo() = scenario(players = 3) {
        alice.battlefield(zarkos)
        alice.library("Plains", "Island")
        carol.battlefield(Plain.idol, Plain.charm, Plain.bear, "Forest")
        advanceTo(TurnStep.End)
        resolveAll()
        answer(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        assertEquals(carol.id, (pending as? Decision.May)?.player)
        answer(Answer.YesNo(true))
        carol.chooses(Plain.idol, Plain.charm)
        playOut()
        expect {
            carol.graveyard has Plain.idol
            carol.graveyard has Plain.charm
            carol.battlefield has Plain.bear
            alice.hand.size shouldBe 0
        }
    }

    // CR 118.12: if they don't, Alice draws two cards
    @Test
    fun zarkosDrawsTwoWhenTheyDont() = scenario {
        alice.battlefield(zarkos)
        alice.library("Plains", "Island")
        bob.battlefield(Plain.idol, Plain.charm)
        advanceTo(TurnStep.End)
        playOut(may = false)
        expect {
            bob.battlefield has Plain.idol
            alice.hand.size shouldBe 2
        }
    }

    // CR 701.23: one card of each name from the graveyard and the library onto the battlefield; the library is shuffled
    @Test
    fun bureauQuartermasterFindsBothNamedCards() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(quartermaster)
        alice.graveyard(lens)
        alice.library(cap, "Plains", "Island")
        alice.casts(quartermaster)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        alice.chooses(lens, cap)
        playOut()
        expect {
            alice.battlefield has lens
            alice.battlefield has cap
        }
    }

    // CR 506.4, 509.1c: with Ravsimir attacking, the targeted creature must block the Wolf if able
    @Test
    fun ravsimirMakesTheTargetBlockTheWolf() = scenario {
        alice.battlefield(ravsimir, wolf)
        bob.battlefield(Plain.turtle, Plain.wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ravsimir, wolf)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(Plain.turtle))))
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(Plain.turtle to ravsimir) }
        bob.blocks(Plain.turtle to wolf)
        expect { state.combat?.blockers.orEmpty().size shouldBe 1 }
    }

    // CR 603.4: without Ravsimir attacking the intervening "if" fails: no trigger
    @Test
    fun ravsimirNeedsToHaveAttacked() = scenario {
        alice.battlefield(ravsimir, wolf)
        bob.battlefield(Plain.turtle)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wolf)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        // no block requirement: Bob may leave the Wolf unblocked
        bob.blocks()
        expect { state.combat?.blockers.orEmpty().size shouldBe 0 }
    }

    // CR 702.80a, 603.10a: wither damage leaves Bob's 1/1 with toughness -1 as it dies: Alice draws
    @Test
    fun carnageLassDrawsForACreatureWitheredBelowOneToughness() = scenario {
        alice.battlefield(lass, Plain.bear)
        alice.library("Plains")
        bob.battlefield(Plain.elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(Plain.bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(Plain.elf to Plain.bear)
        advanceTo(TurnStep.EndOfCombat)
        playOut()
        expect {
            bob.graveyard has Plain.elf
            alice.hand has "Plains"
        }
    }

    // CR 603.10a: a creature destroyed with positive toughness draws nothing
    @Test
    fun carnageLassIgnoresDestroyedCreatures() = scenario {
        alice.battlefield(lass, "Swamp")
        alice.hand(Plain.doom)
        alice.library("Plains")
        bob.battlefield(Plain.bear)
        alice.casts(Plain.doom, target = Plain.bear)
        playOut()
        expect {
            bob.graveyard has Plain.bear
            alice.hand lacks "Plains"
        }
    }

    // CR 121.2: three cards drawn this turn: X is 2
    @Test
    fun quillsPerfectRecallCountsDrawsMinusOne() = scenario {
        alice.battlefield(recall, Plain.bear, "Island", "Island", "Island")
        alice.hand(Plain.study, Plain.study, Plain.study)
        alice.library("Plains", "Plains", "Plains")
        repeat(3) {
            alice.casts(Plain.study)
            playOut()
        }
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(Plain.bear)
        expect { Plain.bear.counters(Plain.p1p1) shouldBe 2 }
    }

    // "more than one card": one draw isn't enough, no trigger
    @Test
    fun quillsPerfectRecallNeedsTwoDraws() = scenario {
        alice.battlefield(recall, Plain.bear, "Island")
        alice.hand(Plain.study)
        alice.library("Plains")
        alice.casts(Plain.study)
        playOut()
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(Plain.bear)
        expect { Plain.bear.counters(Plain.p1p1) shouldBe 0 }
    }

    // CR 508.1: Bob attacks Carol with five creatures: both abilities trigger for Alice; 3 damage to each of her opponents
    @Test
    fun aurelixTriggersOnAnyPlayersAttack() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(aurelix)
        alice.library("Plains", "Island")
        bob.battlefield(Plain.bear, Plain.ogre, Plain.giant, Plain.elf, Plain.golem)
        bob.library("Plains")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(Plain.bear, Plain.ogre, Plain.giant, Plain.elf, Plain.golem, defender = "carol")
        playOut()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 23
            bob.life shouldBe 17
            carol.life shouldBe 17
        }
    }

    // CR 508.1: two attackers aren't three: nothing
    @Test
    fun aurelixIgnoresSmallAttacks() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(aurelix)
        alice.library("Plains")
        bob.battlefield(Plain.bear, Plain.ogre)
        bob.library("Plains")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(Plain.bear, Plain.ogre)
        playOut()
        expect { alice.hand.size shouldBe 0 }
    }

    private fun Scenario.bobAttacks(vararg attacks: Pair<String, arcanum.engine.scenario.ScenarioPlayer>) =
        perform(arcanum.engine.input.Input.DeclareAttackers(bob.id, attacks.map { (name, defender) ->
            arcanum.engine.input.Attack(card(name), arcanum.engine.state.TargetRef.Player(defender.id)) }))

    // CR 508.1, 506.2: two of Bob's attackers attack Alice: Bob (the attacking player) loses 3 and Alice draws; Carol's Tormik doesn't trigger
    @Test
    fun tormikPunishesTwoAttackersAtYou() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(tormik)
        alice.library("Plains")
        bob.battlefield(Plain.bear, Plain.ogre, Plain.elf)
        bob.library("Plains")
        advanceTo(TurnStep.DeclareAttackers)
        bobAttacks(Plain.bear to alice, Plain.ogre to alice, Plain.elf to carol)
        playOut()
        expect {
            bob.life shouldBe 17
            alice.hand has "Plains"
        }
    }

    // CR 508.1: only one attacker at Alice (two at Carol): no trigger
    @Test
    fun tormikNeedsTwoAttackingYou() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(tormik)
        bob.battlefield(Plain.bear, Plain.ogre, Plain.elf)
        bob.library("Plains")
        advanceTo(TurnStep.DeclareAttackers)
        bobAttacks(Plain.bear to alice, Plain.ogre to carol, Plain.elf to carol)
        playOut()
        expect { bob.life shouldBe 20 }
    }

    // CR 603.12, 608.2h: discarding a 5 and a 1, the reflexive trigger deals 5 to each creature
    @Test
    fun untimelyBlastDealsTheGreatestDiscardedManaValue() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", Plain.wall)
        alice.hand(blast)
        alice.library(Plain.giant, Plain.elf)
        bob.battlefield(Plain.turtle, Plain.dragon)
        alice.casts(blast)
        // the two drawn cards are the only ones in hand: discarding them asks nothing more
        playOut(may = true)
        expect {
            alice.graveyard has Plain.giant
            bob.graveyard has Plain.turtle
            bob.battlefield lacks Plain.dragon
            alice.graveyard has Plain.wall
        }
    }

    // CR 603.12: declining the discard, nothing is dealt
    @Test
    fun untimelyBlastMayDecline() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain")
        alice.hand(blast)
        alice.library(Plain.giant, Plain.elf)
        bob.battlefield(Plain.turtle)
        alice.casts(blast)
        playOut(may = false)
        expect {
            alice.hand has Plain.giant
            Plain.turtle.view.obj?.damage shouldBe 0
        }
    }

    // CR 719.3a: the Case is solved at the end step: Alice "solves a Case" and the examiner looks again; CR 609.4b: an Island pays for {W}
    @Test
    fun dossierExaminerTriggersWhenACaseIsSolved() = scenario {
        alice.battlefield(examiner, "Island")
        alice.hand(idleCase)
        alice.library(Plain.charm, "Plains", "Island", "Swamp", "Mountain", "Forest", "Plains")
        alice.casts(idleCase)
        pay()
        playOut()
        expect { alice.battlefield has idleCase }
        advanceTo(TurnStep.End)
        playOut(Plain.charm)
        expect { alice.hand has Plain.charm }
    }

    // CR 701.21a: Bob sacrificing his artifact triggers; "them" is Bob
    @Test
    fun spitefulPursuerPunishesAnOpponentsSacrifice() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(pursuer)
        bob.battlefield(bauble)
        bob.library("Plains")
        bob.activates(bauble)
        playOut()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 20
        }
    }

    // CR 701.21a: Alice's own sacrifice doesn't trigger
    @Test
    fun spitefulPursuerIgnoresYourOwnSacrifice() = scenario {
        alice.battlefield(pursuer, bauble)
        alice.activates(bauble)
        playOut()
        expect {
            alice.life shouldBe 21
            bob.life shouldBe 20
        }
    }
}
