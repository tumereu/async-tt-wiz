package arcanum.engine.sets.mh3

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The MH3 cards of chunk 11, played: the invented names carry the oracle text of Hydroelectric Specimen, Genku, Future Shaper, Victimize,
 * Estrid's Invocation, Nesting Grounds, Ral and the Implicit Maze, Monstrous Vortex, Breathe Your Last, Emrakul, the World Anew and Nadu,
 * Winged Wisdom.
 */
class Mh3Chunk11EffectsScenariosTest {
    private val oddity = Cards.inventFaces("ModalDoubleFaced",
        face("Spillway Oddity", "{2}{U}", "Creature — Weird",
            "Flash\nWhen this creature enters, you may change the target of target instant or sorcery spell with a single target to this creature.",
            "1/4", null, null),
        face("Spillway Works", null, "Land", "As this land enters, you may pay 3 life. If you don't, it enters tapped.\n{T}: Add {U}.", null, null, null))
    private val mirelle = Cards.invent("Mirelle, Shape Weaver", "{2}{W}{U}", "Legendary Creature — Moonfolk Wizard", """
        Whenever another nontoken permanent you control leaves the battlefield, choose one that hasn't been chosen this turn. Create a creature token with those characteristics.
        • 2/2 white Fox with vigilance.
        • 1/2 blue Moonfolk with flying.
        • 1/1 black Rat with lifelink.
        {3}{W}{U}: Put a +1/+1 counter on each creature you control.
    """, "2/5")
    private val exchange = Cards.invent("Grim Exchange", "{2}{B}", "Sorcery",
        "Choose two target creature cards in your graveyard. Sacrifice a creature. If you do, return the chosen cards to the battlefield tapped.")
    private val ritual = Cards.invent("Echoing Ritual", "{2}{U}", "Enchantment",
        "You may have this enchantment enter as a copy of an enchantment you control, except it has \"At the beginning of your upkeep, you may exile this enchantment. If you do, return it to the battlefield under its owner's control.\"")
    private val hollow = Cards.invent("Brooding Hollow", null, "Land", """
        {T}: Add {C}.
        {1}, {T}: Move a counter from target permanent you control onto a second target permanent. Activate only as a sorcery.
    """)
    private val vexa = Cards.invent("Vexa and the Spiral Vault", "{3}{R}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — This Saga deals 2 damage to each creature and planeswalker your opponents control.
        II — You may discard a card. If you do, exile the top two cards of your library. You may play them until the end of your next turn.
        III — Create a Spellsipper Weird token. (It's a {2}{R} 2/2 Weird creature with "Whenever you cast a noncreature spell, put a +1/+1 counter on Spellsipper Weird.")
    """)
    private val maelstrom = Cards.invent("Titanic Maelstrom", "{3}{G}", "Enchantment",
        "Whenever you cast a creature spell with power 5 or greater, discover X, where X is that spell's mana value. (Exile cards from the top of your library until you exile a nonland card with that mana value or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)")
    private val exhale = Cards.invent("Last Exhale", "{1}{B}{B}", "Instant", "Destroy target creature or planeswalker. You gain 1 life for each of its colors.")
    private val vorzhal = Cards.invent("Vorzhal, the Unbound", "{12}", "Legendary Creature — Eldrazi", """
        When you cast this spell, gain control of all creatures target player controls.
        Flying, protection from spells and from permanents that were cast this turn
        When Vorzhal leaves the battlefield, sacrifice all creatures you control.
        Madness—Pay six {C}.
    """, "12/12")
    private val pellis = Cards.invent("Pellis, Feathered Mind", "{1}{G}{U}", "Legendary Creature — Bird Wizard", """
        Flying
        Creatures you control have "Whenever this creature becomes the target of a spell or ability, reveal the top card of your library. If it's a land card, put it onto the battlefield. Otherwise, put it into your hand. This ability triggers only twice each turn."
    """, "3/4")

    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Tallowbone Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val titan = Cards.invent("Hollow Titan", "{7}", "Creature — Eldrazi", "", "7/7")
    private val hound = Cards.invent("Thornfield Hound", "{1}{W}", "Creature — Dog", "", "2/2")
    private val behemoth = Cards.invent("Ridgeback Behemoth", "{4}{G}", "Creature — Beast", "", "5/5")
    private val duelist = Cards.invent("Ashen Duelist", "{B}{R}", "Creature — Human Warrior", "", "2/2")
    private val ambusher = Cards.invent("Ambush Spider", "{1}{G}", "Creature — Spider", "Flash\nReach", "2/4")
    private val vinecrest = Cards.invent("Vinecrest Spider", "{1}{G}", "Creature — Spider", "Reach", "1/3")
    private val anthem = Cards.invent("Glowing Anthem", "{2}{W}", "Enchantment", "Creatures you control get +1/+1.")
    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")
    private val bolt = Cards.invent("Shardless Bolt", "{2}", "Instant", "Shardless Bolt deals 2 damage to any target.")
    private val swell = Cards.invent("Quick Swell", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val insight = Cards.invent("Quick Insight", "{1}{U}", "Instant", "Draw a card.")
    private val topple = Cards.invent("Topple the Mighty", "{2}{W}", "Sorcery", "Destroy each creature with power 7 or greater.")
    private val leechThrull = Cards.invent("Leech Thrull", "{B}", "Creature — Thrull", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        {T}, Pay 2 life: Remove a counter from target nonland permanent.
    """, "1/1")
    private val flats = Cards.invent("Hollowstone Flats", null, "Land", "{T}: Add {C}.")

    /** Answers open "may" decisions with [yes] (an optional choice of one object with the first option or none), resolving on until the
     * Stack is empty or another decision is open. */
    private fun Scenario.resolveAnswering(yes: Boolean) {
        var guard = 30
        while (guard-- > 0) {
            when (pending) {
                is Decision.May -> answer(Answer.YesNo(yes))
                is Decision.ChooseObjects -> answer(Answer.Objects(if (yes) listOf((pending as Decision.ChooseObjects).options.first()) else emptyList()))
                null -> if (state.stack.isEmpty()) return else resolveAll()
                else -> return
            }
        }
    }

    // CR 115.7a: the opponent's burn spell aimed at the Bear now deals its damage to the Oddity
    @Test
    fun oddityTakesTheSpellsTarget() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield("Island", "Island", "Island", bear)
        bob.battlefield("Mountain")
        bob.hand(cinder)
        alice.hand(oddity)
        bob.casts(cinder, target = bear)
        bob.passes()
        alice.casts(oddity)
        resolveAnswering(true)
        expect {
            alice.battlefield has bear
            card("Spillway Oddity").view.obj?.damage shouldBe 2
        }
    }

    // CR 115.7a: declining leaves the target as it is
    @Test
    fun oddityMayLeaveTheTarget() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield("Island", "Island", "Island", bear)
        bob.battlefield("Mountain")
        bob.hand(cinder)
        alice.hand(oddity)
        bob.casts(cinder, target = bear)
        bob.passes()
        alice.casts(oddity)
        resolveAnswering(false)
        expect {
            alice.graveyard has bear
            card("Spillway Oddity").view.obj?.damage shouldBe 0
        }
    }

    // CR 700.2: the second permanent leaving this turn can't choose the Fox again; each mode creates its token
    @Test
    fun mirelleChoosesAnUnchosenModeEachTime() = scenario {
        alice.battlefield(mirelle, bear, hound, "Mountain", "Mountain", "Mountain")
        alice.hand(cinder, bolt)
        alice.casts(cinder, target = bear)
        resolveAll()
        modes(0)
        resolveAll()
        alice.casts(bolt, target = hound)
        resolveAll()
        assertFalse(decision<Decision.Modes>().options[0].available)
        modes(1)
        resolveAll()
        expect {
            alice.battlefield has "Fox Token"
            alice.battlefield has "Moonfolk Token"
            "Fox Token" hasKeyword arcanum.api.Keyword.Vigilance.kind
            "Moonfolk Token".power shouldBe 1
            "Moonfolk Token".toughness shouldBe 2
            "Moonfolk Token".colors shouldBe listOf(Color.Blue)
        }
    }

    // CR 701.21a, 608.2c: a creature is sacrificed, so both chosen cards return tapped
    @Test
    fun grimExchangeReturnsBothTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", hound)
        alice.graveyard(bear, ogre)
        alice.hand(exchange)
        alice.casts(exchange, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.graveyard has hound
            alice.battlefield has bear
            alice.battlefield has ogre
            bear.tapped shouldBe true
            ogre.tapped shouldBe true
        }
    }

    // CR 701.21a: with no creature to sacrifice, nothing returns
    @Test
    fun grimExchangeReturnsNothingWithoutASacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(bear, ogre)
        alice.hand(exchange)
        alice.casts(exchange, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has ogre
        }
    }

    // CR 707.9b, 614.12: a copy of the anthem with the upkeep ability; at the upkeep it is exiled and returns, entering as itself when no
    // copy is chosen (CR 400.7)
    @Test
    fun ritualCopiesAnEnchantmentAndBlinksAtUpkeep() = scenario {
        alice.battlefield("Island", "Island", "Island", anthem, bear)
        alice.hand(ritual)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(ritual)
        resolveAnswering(true)
        expect { bear.power shouldBe 4 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        may(true)
        resolveAnswering(false)
        expect {
            alice.battlefield has ritual
            bear.power shouldBe 3
        }
    }

    // CR 122.1, 608.2d: one counter, of the kind chosen, moves to the second target
    @Test
    fun hollowMovesOneCounterOfTheChosenKind() = scenario {
        alice.battlefield(hollow, flats)
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 2))
        alice.battlefield(ogre)
        alice.activates(hollow, ability = 1, targets = listOf(bear, ogre))
        resolve()
        option("oil")
        expect {
            bear.counters(CounterKind.Named("oil")) shouldBe 1
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            ogre.counters(CounterKind.Named("oil")) shouldBe 1
            ogre.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 608.2d: "Remove a counter" from a permanent with two kinds removes one of the kind its controller chooses (it removed the first
    // kind without asking)
    @Test
    fun removeACounterAsksForTheKind() = scenario {
        alice.battlefield(leechThrull)
        bob.battlefield(ogre, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 1))
        alice.activates(leechThrull, target = ogre)
        resolve()
        option("oil")
        expect {
            ogre.counters(CounterKind.Named("oil")) shouldBe 0
            ogre.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 115.3: the second target is another permanent than the first
    @Test
    fun hollowNeedsTwoDifferentPermanents() = scenario {
        alice.battlefield(hollow, flats)
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        assertFailsWith<IllegalArgumentException> { alice.activates(hollow, ability = 1, targets = listOf(bear, bear)) }
    }

    // CR 714.2b, 111.4, 202.2: chapter III's token is a red 2/2 Weird with mana value 3 that grows on noncreature spells
    @Test
    fun vexaMakesTheReminderToken() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(insight)
        loreChapter(vexa, 3, "Island")
        resolveAll()
        expect {
            alice.battlefield has "Spellsipper Weird"
            "Spellsipper Weird".colors shouldBe listOf(Color.Red)
            "Spellsipper Weird".view.characteristics?.manaValue() shouldBe 3
            alice.graveyard has vexa
        }
        alice.casts(insight)
        resolveAll()
        expect { "Spellsipper Weird".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 701.57a: discover 5 (the Behemoth's mana value) passes over the 7-mana Titan and finds the Ogre, here put into the hand
    @Test
    fun maelstromDiscoversTheSpellsManaValue() = scenario {
        alice.battlefield(maelstrom, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(behemoth)
        alice.library(titan, ogre, "Island")
        alice.casts(behemoth)
        resolveAnswering(false)
        expect {
            alice.hand has ogre
            alice.library has titan
            alice.battlefield has behemoth
        }
    }

    // CR 105.2, 608.2h: a black and red creature destroyed gives 2 life
    @Test
    fun exhaleGainsForEachColorOfTheDestroyed() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        bob.battlefield(duelist)
        alice.hand(exhale)
        alice.casts(exhale, target = duelist)
        resolveAll()
        expect {
            bob.graveyard has duelist
            alice.life shouldBe 22
        }
    }

    // CR 603.6, 613.1b: casting it takes every creature of the targeted player (three players: only Carol's)
    @Test
    fun vorzhalTakesTheTargetedPlayersCreatures() = scenario(players = 3) {
        alice.hand(vorzhal)
        bob.battlefield(ogre)
        carol.battlefield(bear, hound)
        alice.castsWithoutCosts(vorzhal)
        targets("carol")
        resolveAll()
        expect {
            bear.controller shouldBe alice.id
            hound.controller shouldBe alice.id
            ogre.controller shouldBe bob.id
        }
    }

    // CR 603.6c, 701.21a: when it leaves, its controller sacrifices all their creatures
    @Test
    fun vorzhalLeavingSacrificesYourCreatures() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(vorzhal, bear)
        bob.battlefield("Plains", "Plains", "Plains", ogre)
        bob.hand(topple)
        bob.casts(topple)
        resolveAll()
        expect {
            alice.graveyard has vorzhal
            alice.graveyard has bear
            bob.battlefield has ogre
        }
    }

    // CR 702.16b, f: no spell can target it, and a creature cast this turn can't block it while one put onto the battlefield earlier can
    @Test
    fun vorzhalHasProtectionFromSpellsAndPermanentsCastThisTurn() = scenario {
        alice.battlefield(vorzhal)
        bob.battlefield("Mountain", "Forest", "Forest", vinecrest)
        bob.hand(cinder, ambusher)
        alice.passes()
        bob.casts(cinder)
        assertFalse(target(vorzhal) in decision<Decision.Targets>().options)
        targets("alice")
        if (pending is Decision.ManaPayment) paySuggested()
        allPass()
        alice.passes()
        bob.casts(ambusher)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vorzhal)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ambusher to vorzhal) }
        bob.blocks(vinecrest to vorzhal)
    }

    // CR 603.2: "This ability triggers only twice each turn": the third time the Bear is targeted nothing is revealed
    @Test
    fun pellisRevealsOnlyTwiceATurn() = scenario {
        alice.battlefield(pellis, bear)
        alice.hand(swell, swell, swell)
        alice.library("Island", ogre, titan)
        repeat(3) {
            alice.castsWithoutCosts(swell, target = bear)
            resolveAll()
        }
        expect {
            alice.battlefield has "Island"
            alice.hand has ogre
            alice.library has titan
            bear.power shouldBe 5
        }
    }
}
