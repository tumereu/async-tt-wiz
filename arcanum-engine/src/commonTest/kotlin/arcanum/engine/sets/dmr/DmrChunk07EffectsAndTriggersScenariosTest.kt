package arcanum.engine.sets.dmr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dominaria Remastered chunk 07 in play: combat damage prevented to and by a creature (CR 615.1), a card that shares a creature type with
 * each creature tapped to pay (CR 205.3m), a return at end of combat (CR 603.7, 511.2), a drawn card revealed and discarded (CR 701.20a,
 * 701.9a), a linked exile paid for as it enters (CR 118.12a, 607.2a), a search simply for three cards (CR 701.23d), a power and toughness
 * equal to the life total (CR 604.3), two extra turns (CR 500.7), mana of the chosen color (CR 106.12a, 605.1b), state triggers
 * (CR 603.8), any player's cycling (CR 702.29a) and damage to a creature or opponent (CR 120.3).
 */
class DmrChunk07EffectsAndTriggersScenariosTest {
    private val maze = Cards.invent("Maze of Ossory", null, "Land",
        "{T}: Untap target attacking creature. Prevent all combat damage that would be dealt to and dealt by that creature this turn.")
    private val portal = Cards.invent("Riddled Portal", "{5}", "Artifact",
        "Tap two untapped creatures you control: You may put a creature card from your hand that shares a creature type with each creature tapped this way onto the battlefield.")
    private val sentry = Cards.invent("Grove Sentry", "{G}", "Creature — Elf Warrior", "", "1/1")
    private val hexer = Cards.invent("Ember Hexer", "{R}", "Creature — Goblin Shaman", "", "1/1")
    private val raider = Cards.invent("Ridge Raider", "{1}{R}", "Creature — Goblin Warrior", "", "2/1")
    private val tender = Cards.invent("Leaf Tender", "{1}{G}", "Creature — Elf Druid", "", "1/2")
    private val wall = Cards.invent("Wall of Scrap Iron", "{2}", "Artifact Creature — Wall", """
        Defender (This creature can't attack.)
        When this creature blocks, return it to its owner's hand at end of combat. (Return it only if it's on the battlefield.)
    """, "0/7")
    private val diviner = Cards.invent("Oasis Diviner", "{1}{G}", "Creature — Human Shaman",
        "{T}: Draw a card and reveal it. If it isn't a land card, discard it.", "1/1")
    private val drake = Cards.invent("Wyrmtooth Drake", "{2}{U}", "Creature — Nightmare Drake", """
        Flying
        When this creature enters, sacrifice it unless you exile a creature you control other than this creature.
        When this creature leaves the battlefield, return the exiled card to the battlefield under its owner's control.
    """, "3/4")
    private val recall = Cards.invent("Tidal Recall", "{U}", "Instant", "Return target creature to its owner's hand.")
    private val bonnet = Cards.invent("Fool's Bonnet", "{4}", "Artifact",
        "{2}, {T}, Sacrifice this artifact: Search target player's library for three cards and exile them. Then that player shuffles.")
    private val seraph = Cards.invent("Seraph Incarnate", "{4}{W}{W}{W}", "Creature — Avatar", """
        Seraph Incarnate's power and toughness are each equal to your life total.
        When Seraph Incarnate is put into a graveyard from anywhere, shuffle it into its owner's library.
    """, "*/*")
    private val warp = Cards.invent("Hour Warp", "{8}{U}{U}", "Sorcery", "Target player takes two extra turns after this one.")
    private val bracer = Cards.invent("Bracer of Might", "{5}", "Artifact", """
        As this artifact enters, choose a color.
        Creatures of the chosen color get +1/+1.
        Whenever a basic land is tapped for mana of the chosen color, its controller adds an additional one mana of that color.
    """)
    private val abyss = Cards.invent("Black Abyss", null, "Legendary Snow Land", """
        Black Abyss enters with ten ice counters on it.
        {3}: Remove an ice counter from Black Abyss.
        When Black Abyss has no ice counters on it, sacrifice it. If you do, create Vorn Hollow, a legendary 20/20 black Avatar creature token with flying and indestructible.
    """)
    private val gift = Cards.invent("Bracing Gift", "{1}{G}", "Enchantment", "Whenever a player cycles a card, you may put a +1/+1 counter on target creature.")
    private val chasm = Cards.invent("Thunder Chasm", "{1}{R}", "Enchantment",
        "Whenever a player cycles a card, you may pay {1}. If you do, this enchantment deals 2 damage to any target.")
    private val scholar = Cards.invent("Sandswept Scholar", "{2}{U}", "Creature — Human Wizard", "Cycling {1} ({1}, Discard this card: Draw a card.)", "1/1")
    private val ripper = Cards.invent("Sinew Ripper", "{1}{B}", "Creature — Phyrexian Horror",
        "Whenever this creature deals damage to a creature or opponent, this creature deals that much damage to you.", "4/4")
    private val barrier = Cards.invent("Tidebarrier", "{3}{U}", "Creature — Wall", """
        Defender
        When this creature has flying, sacrifice it.
        When this creature leaves the battlefield, it deals damage to each nonblue creature without flying equal to half the number of Islands you control, rounded down.
    """, "0/5")
    private val wings = Cards.invent("Sudden Wings", "{U}", "Instant", "Target creature gains flying until end of turn.")
    private val sentinel = Cards.invent("Azure Sentinel", "{1}{U}", "Creature — Human Soldier", "", "2/2")
    private val kite = Cards.invent("Sky Kite", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    private val gambit = Cards.invent("Final Gambit", "{R}{R}", "Sorcery",
        "Take an extra turn after this one. At the beginning of that turn's end step, you lose the game.")
    private val reprieve = Cards.invent("Hasty Reprieve", "{1}{U}", "Sorcery", "Take an extra turn after this one.")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Alley Wolf", "{1}{G}", "Creature — Wolf", "", "3/3")

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    // CR 615.1, 510.2: the defender's land untaps the attacker; the 3/3 blocker deals it no damage and takes none
    @Test
    fun mazeUntapsTheAttackerAndPreventsCombatDamageBothWays() = scenario {
        alice.battlefield(bear)
        bob.battlefield(maze, wolf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.passes()
        bob.activates(maze, target = bear)
        resolve()
        expect { bear.tapped shouldBe false }
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wolf to bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.battlefield has bear
            card(bear).view.obj!!.damage shouldBe 0
            card(wolf).view.obj!!.damage shouldBe 0
        }
    }

    // CR 118.3, 205.3m: the Goblin Warrior shares a type with the Elf Warrior and with the Goblin Shaman; the Elf Druid not with the Goblin
    @Test
    fun portalOffersOnlyACardSharingATypeWithEachTappedCreature() = scenario {
        alice.battlefield(portal, sentry, hexer)
        alice.hand(raider, tender)
        alice.activates(portal, choosing = listOf(sentry, hexer))
        resolve()
        assertEquals(listOf(card(raider)), decision<Decision.ChooseObjects>().options)
        alice.chooses(raider)
        expect {
            alice.battlefield has raider
            alice.hand has tender
            sentry.tapped shouldBe true
            hexer.tapped shouldBe true
        }
    }

    // CR 603.7, 511.2: the Wall blocks, survives combat damage and returns to its owner's hand at end of combat
    @Test
    fun wallReturnsToHandAtEndOfCombat() = scenario {
        alice.battlefield(bear)
        bob.battlefield(wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to bear)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        expect { bob.battlefield has wall }
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.hand has wall
            bob.battlefield lacks wall
        }
    }

    // CR 701.20a, 701.9a: a nonland card drawn this way is discarded
    @Test
    fun divinerDiscardsANonlandCard() = scenario {
        alice.battlefield(diviner)
        alice.library(bear, "Forest")
        alice.activates(diviner)
        resolve()
        expect {
            alice.graveyard has bear
            alice.hand lacks bear
        }
    }

    // CR 701.20a: a land card drawn this way stays in hand
    @Test
    fun divinerKeepsALandCard() = scenario {
        alice.battlefield(diviner)
        alice.library("Forest", bear)
        alice.activates(diviner)
        resolve()
        expect {
            alice.hand has "Forest"
            alice.graveyard.isEmpty()
        }
    }

    // CR 603.6a, 400.7: put into the graveyard from the hand (discarded), it is shuffled into its owner's library
    @Test
    fun seraphDiscardedFromHandIsShuffledIntoTheLibrary() = scenario {
        alice.battlefield(diviner)
        alice.library(seraph, "Forest")
        alice.activates(diviner)
        resolve()
        resolveAll()
        expect {
            alice.library has seraph
            alice.graveyard lacks seraph
        }
    }

    // CR 604.3, 119: its power and toughness follow the life total
    @Test
    fun seraphIsAsBigAsTheLifeTotal() = scenario {
        alice.life = 13
        alice.battlefield(seraph)
        expect {
            seraph.power shouldBe 13
            seraph.toughness shouldBe 13
        }
    }

    // CR 118.12a, 607.2a, 610.3: the Bear is exiled as the cost, and it returns when the Drake leaves
    @Test
    fun drakeExilesACreatureAndReturnsItWhenItLeaves() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(drake, recall)
        alice.casts(drake)
        resolve()
        resolve()
        yes()
        if (pending != null) alice.chooses(bear)
        expect {
            alice.exile has bear
            alice.battlefield has drake
        }
        alice.casts(recall, target = drake)
        resolveAll()
        expect {
            alice.hand has drake
            alice.battlefield has bear
        }
    }

    // CR 118.12a: not paid, the Drake is sacrificed and nothing returns
    @Test
    fun drakeIsSacrificedWithoutTheExile() = scenario {
        alice.battlefield(bear)
        alice.hand(drake)
        alice.castsWithoutCosts(drake)
        resolve()
        resolve()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.graveyard has drake
            alice.battlefield has bear
        }
    }

    // CR 701.23d: searching simply for three cards, three of the five must be found
    @Test
    fun bonnetMustFindThreeCards() = scenario {
        alice.battlefield(bonnet, "Swamp", "Swamp")
        bob.library("Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.activates(bonnet, target = "bob")
        resolve()
        val search = decision<Decision.ChooseObjects>()
        assertEquals(3, search.min)
        answer(Answer.Objects(search.options.take(3)))
        expect {
            bob.exile.size shouldBe 3
            bob.library.size shouldBe 2
        }
    }

    // CR 701.23d: as many as possible when the library has fewer
    @Test
    fun bonnetFindsBothCardsOfATwoCardLibrary() = scenario {
        alice.battlefield(bonnet, "Swamp", "Swamp")
        bob.library("Plains", "Island")
        alice.activates(bonnet, target = "bob")
        resolve()
        if (pending != null) {
            val search = decision<Decision.ChooseObjects>()
            assertEquals(2, search.min)
            answer(Answer.Objects(search.options))
        }
        expect {
            bob.exile.size shouldBe 2
            bob.library.isEmpty()
        }
    }

    // CR 500.7: the target player takes the next two turns
    @Test
    fun warpGivesTheTargetPlayerTwoExtraTurns() = scenario(players = 3) {
        alice.hand(warp)
        alice.castsWithoutCosts(warp, target = "bob")
        resolve()
        assertEquals(listOf(bob.id, bob.id), state.turn.extraTurns)
    }

    // CR 106.12a, 605.1b, 613.4c: green basic lands add one more green, of any controller; a Plains doesn't; green creatures get +1/+1
    @Test
    fun bracerAddsManaOfTheChosenColor() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Forest", "Plains", bear)
        bob.battlefield("Forest")
        alice.hand(bracer)
        alice.casts(bracer, paying = listOf("Island", "Island#1", "Island#2", "Island#3", "Island#4"))
        resolveAll()
        alice.answers(Answer.ColorChoice(Color.Green))
        alice.activates("Forest")
        expect {
            alice.pool.map { it.color } shouldBe listOf(Color.Green, Color.Green)
            bear.power shouldBe 3
        }
        alice.activates("Plains")
        expect { alice.pool.size shouldBe 3 }
        alice.passes()
        bob.activates("Forest")
        expect { bob.pool.map { it.color } shouldBe listOf(Color.Green, Color.Green) }
    }

    // CR 122.1, 603.8, 701.21a: the last ice counter removed, it is sacrificed and the 20/20 is created
    @Test
    fun abyssMakesTheGiantWhenTheLastCounterIsRemoved() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.battlefield(abyss, counters = listOf(CounterKind.Named("ice") to 1))
        alice.activates(abyss)
        resolveAll()
        expect {
            alice.graveyard has abyss
            alice.battlefield has "Vorn Hollow"
            "Vorn Hollow".power shouldBe 20
            "Vorn Hollow" hasKeyword Keyword.Flying.kind
            "Vorn Hollow" hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 702.29a: another player's cycling triggers it
    @Test
    fun giftPutsACounterWhenAnyPlayerCycles() = scenario(players = 3) {
        alice.battlefield(gift, bear)
        carol.battlefield("Island")
        carol.hand(scholar)
        carol.library("Plains")
        turn(priority = carol)
        carol.activates(scholar)
        resolveAll()
        yes()
        resolveAll()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            carol.hand has "Plains"
        }
    }

    // CR 702.29a, 118.12: paid {1}, 2 damage to the target
    @Test
    fun chasmDealsDamageWhenPaid() = scenario {
        alice.battlefield(chasm, "Mountain")
        bob.battlefield("Island")
        bob.hand(scholar)
        bob.library("Plains")
        turn(priority = bob)
        bob.activates(scholar)
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        yes()
        if (pending != null) answer(decision<Decision.ManaPayment>().let { Answer.ManaPayment(it.suggestion) })
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 120.3, 603.2: unblocked, it deals 4 to the defending opponent and then 4 to its controller
    @Test
    fun ripperHurtsItsControllerAsMuchAsTheOpponent() = scenario(players = 3) {
        alice.battlefield(ripper)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ripper, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.life shouldBe 16
            carol.life shouldBe 20
        }
    }

    // CR 120.3: blocked by a 2/2, it deals all 4 to the creature, and that much to its controller
    @Test
    fun ripperHurtsItsControllerForDamageToACreature() = scenario {
        alice.battlefield(ripper)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ripper)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to ripper)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.life shouldBe 16
            bob.life shouldBe 20
        }
    }

    // CR 603.8, 107.1a: given flying, it is sacrificed and deals 2 (half of five Islands) to each nonblue creature without flying
    @Test
    fun barrierWithFlyingIsSacrificedAndDealsHalfTheIslands() = scenario {
        alice.battlefield(barrier, "Island", "Island", "Island", "Island", "Island")
        alice.hand(wings)
        bob.battlefield(bear, sentinel, kite)
        alice.castsWithoutCosts(wings, target = barrier)
        resolveAll()
        expect {
            alice.graveyard has barrier
            bob.graveyard has bear
            bob.battlefield has sentinel
            bob.battlefield has kite
        }
    }

    // CR 500.7, 603.7: a second extra turn created later is taken first; the loss waits for the end step of the gambit's own turn
    @Test
    fun gambitLosesAtTheEndOfItsOwnExtraTurn() = scenario {
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island")
        alice.hand(gambit, reprieve)
        alice.castsWithoutCosts(gambit)
        resolve()
        alice.castsWithoutCosts(reprieve)
        resolve()
        advanceTo(TurnStep.End, turn = 2)
        resolveAll()
        assertEquals(alice.id, state.turn.active)
        assertEquals(false, state.player(alice.id).lost)
        advanceTo(TurnStep.End, turn = 3)
        resolveAll()
        assertEquals(alice.id, state.turn.active)
        assertEquals(true, state.player(alice.id).lost)
    }
}
