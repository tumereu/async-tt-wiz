package arcanum.engine.sets.dmr

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Dominaria Remastered chunk 10 in play: flashback with a cost of tapping white creatures, sacrificing creatures or paying life
 * (CR 702.34a, 118.3), echo, landwalk of the defending player (CR 702.14c), protection (CR 702.16) and storm (CR 702.40a).
 */
class DmrChunk10EchoFlashbackLandwalkProtectionStormScenariosTest {
    private val tail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    private val screech = Cards.invent("Gull Screech", "{2}{W}{W}", "Sorcery", """
        Create two 1/1 white Bird creature tokens with flying.
        Flashback—Tap three untapped white creatures you control. $tail
    """)
    private val herd = Cards.invent("Call of the Pack", "{2}{G}", "Sorcery", """
        Create a 3/3 green Elephant creature token.
        Flashback {3}{G} $tail
    """)
    private val writ = Cards.invent("Chainer's Writ", "{1}{B}", "Sorcery", """
        Target player sacrifices a creature of their choice.
        Flashback {5}{B}{B} $tail
    """)
    private val reflection = Cards.invent("Deep Reflection", "{3}{U}", "Sorcery", """
        Target player draws two cards.
        Flashback—{1}{U}, Pay 3 life. $tail
    """)
    private val recall = Cards.invent("Dread Recall", "{2}{B}{B}", "Sorcery", """
        Return target creature card from your graveyard to the battlefield.
        Flashback—Sacrifice three creatures. $tail
    """)
    private val shift = Cards.invent("Momentary Shift", "{1}{W}", "Instant", """
        Exile target creature you control, then return it to the battlefield under its owner's control.
        Flashback {3}{U} $tail
    """)
    private val recovery = Cards.invent("Sevinne's Recovery", "{2}{W}", "Sorcery", """
        Return target permanent card with mana value 3 or less from your graveyard to the battlefield. If this spell was cast from a graveyard, you may copy this spell and may choose a new target for the copy.
        Flashback {4}{W} $tail
    """)
    private val captain = Cards.invent("Mogg Raider Captain", "{1}{R}", "Creature — Goblin Warrior", """
        Echo {1}{R} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
        When this creature enters or dies, create a 1/1 red Goblin creature token.
    """, "1/1")
    private val vault = Cards.invent("Urza's Blueprint Vault", "{6}", "Artifact", """
        Echo {6} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
        {T}: Draw a card.
    """)
    private val marshKing = Cards.invent("Sol'kanar the Marsh King", "{2}{U}{B}{R}", "Legendary Creature — Demon", """
        Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
        Whenever a player casts a black spell, you gain 1 life.
    """, "5/5")
    private val warden = Cards.invent("Mystic Warden", "{2}{G}{W}", "Creature — Human Nomad Mystic", """
        Protection from black
        Threshold — As long as there are seven or more cards in your graveyard, this creature gets +3/+3 and has flying.
    """, "3/3")
    private val cat = Cards.invent("Spectral Cat", "{1}{W}", "Creature — Cat Spirit", """
        Protection from green
        {B}: Regenerate this creature.
    """, "2/1")
    private val surgeon = Cards.invent("Yawgmoth, Thran Surgeon", "{2}{B}{B}", "Legendary Creature — Human Cleric", """
        Protection from Humans
        Pay 1 life, Sacrifice another creature: Put a -1/-1 counter on up to one target creature and draw a card.
        {B}{B}, Discard a card: Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
    """, "2/4")
    private val burrows = Cards.invent("Empty the Burrows", "{3}{R}", "Sorcery", """
        Create two 1/1 red Goblin creature tokens.
        Storm (When you cast this spell, copy it for each spell cast before it this turn.)
    """)
    private val volley = Cards.invent("Grapeshot Volley", "{1}{R}", "Sorcery", """
        Grapeshot Volley deals 1 damage to any target.
        Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
    """)
    private val ox = Cards.invent("Tarnished Ox", "{2}", "Creature — Ox", "", "2/2")
    private val knight = Cards.invent("Dawn Knight", "{W}", "Creature — Human Knight", "", "1/1")
    private val ranger = Cards.invent("Moss Ranger", "{G}", "Creature — Elf Archer", "", "2/2")
    private val brute = Cards.invent("Moor Brute", "{3}{G}", "Creature — Beast", "", "4/4")
    private val rat = Cards.invent("Gutter Rat", "{B}", "Creature — Rat", "", "1/1")
    private val bolt = Cards.invent("Dusk Bolt", "{B}", "Instant", "Destroy target creature.")
    private val shock = Cards.invent("Emberling Jolt", "{R}", "Instant", "Emberling Jolt deals 1 damage to any target.")
    private val whisper = Cards.invent("Quiet Whisper", "{U}", "Instant", "You gain 1 life.")
    private val libs = (1..9).map { Cards.invent("Library Card $it", "{1}", "Sorcery", "") }

    private fun Scenario.count(kind: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == kind }

    // CR 702.34a, 118.3: the cost taps three untapped white creatures you control; the spell is exiled afterwards
    @Test
    fun screechFlashbackTapsThreeOfYourWhiteCreatures() = scenario {
        alice.battlefield(knight, "Plains", "Plains", "Plains", "Plains")
        alice.hand(screech)
        alice.casts(screech)
        resolveAll()
        expect {
            count("Bird Token") shouldBe 2
            screech.zone shouldBe Zone.Graveyard
        }
        alice.casts(screech)
        resolveAll()
        expect {
            count("Bird Token") shouldBe 4
            screech.zone shouldBe Zone.Exile
        }
    }

    // CR 118.3: only creatures you control that are untapped and white pay; an opponent's or a green one can't
    @Test
    fun screechFlashbackCantTapOthersOrNonWhite() = scenario {
        alice.battlefield(knight, knight, ranger)
        bob.battlefield(knight)
        alice.graveyard(screech)
        // two white creatures of alice's only: three can't be found
        assertFails { alice.casts(screech) }
        assertEquals(Zone.Graveyard, state.obj(card(screech))!!.zone)
    }

    // CR 702.34a: the flashback cost is the one named, not the printed one
    @Test
    fun herdFlashbackCostsItsOwnMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.graveyard(herd)
        alice.casts(herd)
        resolveAll()
        expect {
            count("Elephant Token") shouldBe 1
            herd.zone shouldBe Zone.Exile
        }
    }

    // CR 701.21a, 3 players: the chosen player sacrifices the creature they choose, nobody else does
    @Test
    fun writFlashbackMakesTheTargetPlayerChoose() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ox)
        bob.battlefield("Tarnished Ox", ranger)
        carol.battlefield(knight)
        alice.graveyard(writ)
        alice.casts(writ, target = "carol")
        resolveAll()
        expect {
            carol.graveyard has knight
            alice.battlefield has ox
            writ.zone shouldBe Zone.Exile
        }
    }

    // CR 118.3, 119.4: the life is part of the cost; with too little life it can't be paid
    @Test
    fun reflectionFlashbackPaysThreeLife() = scenario {
        alice.battlefield("Island", "Island")
        alice.graveyard(reflection)
        alice.library(*libs.toTypedArray())
        alice.casts(reflection, target = "alice")
        resolveAll()
        expect {
            alice.life shouldBe 17
            alice.hand.size shouldBe 2
            reflection.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun reflectionFlashbackNeedsTheLife() = scenario {
        alice.battlefield("Island", "Island")
        alice.life = 2
        alice.graveyard(reflection)
        assertFails { alice.casts(reflection, target = "alice") }
        assertEquals(2, alice.life)
    }

    // CR 701.21a, 118.3: sacrificing three creatures is the cost; it returns a creature card, even a sacrificed one (CR 400.7)
    @Test
    fun recallFlashbackSacrificesThreeCreatures() = scenario {
        alice.battlefield(ox, knight, ranger)
        alice.graveyard(recall, rat)
        alice.casts(recall, target = rat)
        resolveAll()
        expect {
            alice.battlefield has rat
            alice.graveyard.size shouldBe 3
            recall.zone shouldBe Zone.Exile
        }
    }

    // CR 118.3: with fewer than three creatures to sacrifice the flashback can't be cast
    @Test
    fun recallFlashbackNeedsThreeCreatures() = scenario {
        alice.battlefield(ox, knight)
        alice.graveyard(recall, ranger)
        assertFails { alice.casts(recall, target = ranger) }
    }

    // CR 702.34a, 400.7: flashback of an instant; the creature returns as a new object
    @Test
    fun shiftFlashbackBlinksTheCreature() = scenario {
        alice.battlefield(ox, "Island", "Island", "Island", "Island")
        alice.graveyard(shift)
        alice.casts(shift, target = ox)
        resolveAll()
        expect {
            alice.battlefield has ox
            shift.zone shouldBe Zone.Exile
        }
    }

    // CR 707.10, 608.2: cast from the graveyard it may be copied, with a new target; cast from the hand it isn't
    @Test
    fun recoveryFlashbackMayCopyItself() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(recovery, ox, knight)
        alice.casts(recovery, target = ox)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target(knight))))
        resolveAll()
        expect {
            alice.battlefield has ox
            alice.battlefield has knight
            recovery.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun recoveryFromHandIsNotCopied() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(recovery)
        alice.graveyard(ox, knight)
        alice.casts(recovery, target = ox)
        resolveAll()
        expect {
            alice.battlefield has ox
            alice.graveyard has knight
            recovery.zone shouldBe Zone.Graveyard
        }
    }

    private fun Scenario.libraries() {
        alice.library(*libs.toTypedArray())
        bob.library(*libs.toTypedArray())
    }

    // CR 603.6a, 700.4: one trigger for entering and for dying; each makes its own Goblin
    @Test
    fun captainMakesATokenWhenItEntersAndWhenItDies() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(captain)
        bob.battlefield("Swamp")
        bob.hand(bolt)
        alice.casts(captain)
        resolveAll()
        expect { count("Goblin Token") shouldBe 1 }
        alice.passes()
        bob.casts(bolt, target = captain)
        resolveAll()
        expect {
            alice.graveyard has captain
            count("Goblin Token") shouldBe 2
        }
    }

    // CR 702.30a: paying the echo cost at the next upkeep keeps it; it isn't asked again
    @Test
    fun captainEchoPaidKeepsItAndIsAskedOnce() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(captain)
        libraries()
        alice.casts(captain, paying = listOf("Mountain", "Mountain#1"))
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect { alice.battlefield has captain }
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect { stack.isEmpty() }
        assertEquals(null, pending)
    }

    // CR 702.30a, 700.4: declining the echo sacrifices it, and the dies trigger then makes a Goblin
    @Test
    fun captainEchoDeclinedSacrificesItAndMakesAToken() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(captain)
        libraries()
        alice.casts(captain)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.graveyard has captain
            count("Goblin Token") shouldBe 2
        }
    }

    // CR 702.30a, 602.1: echo of {6} not paid sacrifices the artifact; paid, its tap ability draws
    @Test
    fun vaultEchoDeclinedSacrifices() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(vault)
        libraries()
        alice.casts(vault)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        alice.answers(Answer.YesNo(false))
        expect { alice.graveyard has vault }
    }

    @Test
    fun vaultTapsToDraw() = scenario {
        alice.battlefield(vault)
        alice.library(*libs.toTypedArray())
        alice.activates(vault)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 702.14c: can't be blocked while the defending player controls a Swamp
    @Test
    fun swampwalkCantBeBlockedWhenDefenderHasASwamp() = scenario {
        alice.battlefield(marshKing)
        bob.battlefield("Swamp", ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(marshKing)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ox to marshKing) }
    }

    // CR 702.14c: only the defending player's lands count; with 3 players, one Swamp stops blocks for the player who owns it only
    @Test
    fun swampwalkLooksAtTheDefendingPlayerOnly() = scenario(players = 3) {
        alice.battlefield(marshKing)
        bob.battlefield("Plains", ox)
        carol.battlefield("Swamp", ranger)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(marshKing, defender = "bob")
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ox to marshKing)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.graveyard has ox }
    }

    @Test
    fun swampwalkAgainstTheThirdPlayerWithASwamp() = scenario(players = 3) {
        alice.battlefield(marshKing)
        bob.battlefield("Plains", ox)
        carol.battlefield("Swamp", ranger)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(marshKing, defender = "carol")
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { carol.blocks(ranger to marshKing) }
    }

    // CR 603.2: "a player" casts: an opponent's black spell counts too
    @Test
    fun marshKingGainsLifeForEveryPlayersBlackSpell() = scenario {
        alice.battlefield(marshKing, ox)
        bob.battlefield("Swamp", "Swamp")
        bob.hand(bolt, bolt)
        turn(number = 2, active = bob)
        bob.casts(bolt, target = ox)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 702.16b, 115.4: a black spell can't target it; threshold (CR 207.2c) makes it 6/6 with flying
    @Test
    fun wardenCantBeTargetedByBlackSpells() = scenario {
        alice.battlefield(warden, ox)
        bob.battlefield("Swamp")
        bob.hand(bolt)
        turn(number = 2, active = bob)
        bob.casts(bolt)
        val options = decision<Decision.Targets>().options
        assertTrue(target(ox) in options)
        assertTrue(target(warden) !in options)
    }

    @Test
    fun wardenThresholdAddsSizeAndFlying() = scenario {
        alice.battlefield(warden)
        alice.graveyard(*Array(6) { "Plains" })
        expect {
            warden.power shouldBe 3
            warden lacksKeyword Keyword.Flying
        }
        alice.graveyard(*Array(1) { "Island" })
        expect {
            warden.power shouldBe 6
            warden.toughness shouldBe 6
            warden hasKeyword Keyword.Flying.kind
        }
    }

    // CR 702.16f: a green creature can't block it
    @Test
    fun catCantBeBlockedByAGreenCreature() = scenario {
        alice.battlefield(cat)
        bob.battlefield(ranger, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cat)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ranger to cat) }
        bob.blocks(ox to cat)
    }

    // CR 702.16e: damage from green sources is prevented; the other side takes damage as usual
    @Test
    fun catTakesNoDamageFromAGreenAttacker() = scenario {
        alice.battlefield(cat)
        bob.battlefield(ranger)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ranger)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(cat to ranger)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.battlefield has cat
            bob.graveyard has ranger
        }
    }

    // CR 702.16c: Humans can't block it
    @Test
    fun surgeonCantBeBlockedByAHuman() = scenario {
        alice.battlefield(surgeon)
        bob.battlefield(knight, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(surgeon)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(knight to surgeon) }
        bob.blocks(ox to surgeon)
    }

    // CR 118.3, 701.21a: the cost is a life and another creature (not itself); a -1/-1 counter lands on the target and a card is drawn
    @Test
    fun surgeonSacrificesAnotherCreatureForACounterAndACard() = scenario {
        alice.battlefield(surgeon, ox)
        bob.battlefield(ranger)
        alice.library(*libs.toTypedArray())
        alice.activates(surgeon, ability = 0, target = ranger)
        resolveAll()
        expect {
            alice.graveyard has ox
            alice.battlefield has surgeon
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
            ranger.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
        }
    }

    // CR 701.34a: proliferate gives another counter of each kind already there
    @Test
    fun surgeonProliferates() = scenario {
        alice.battlefield(surgeon, "Swamp", "Swamp")
        alice.hand("Island")
        bob.battlefield(ranger)
        alice.battlefield(brute, counters = listOf(CounterKind.PowerToughness(-1, -1) to 1))
        alice.activates(surgeon, ability = 1, choosing = listOf("Island"))
        resolveAll()
        answer(Answer.Targets(listOf(target(brute))))
        resolveAll()
        expect { brute.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2 }
    }

    // CR 702.40a: a copy for each spell cast before it this turn
    @Test
    fun stormMakesACopyPerEarlierSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island", "Island")
        alice.hand(whisper, whisper, burrows)
        alice.casts(whisper)
        resolveAll()
        alice.casts(whisper)
        resolveAll()
        alice.casts(burrows)
        resolveAll()
        expect { count("Goblin Token") shouldBe 6 }
    }

    // CR 702.40a: the first spell of the turn has no copies
    @Test
    fun stormAsTheFirstSpellMakesNoCopy() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(burrows)
        alice.casts(burrows)
        resolveAll()
        expect { count("Goblin Token") shouldBe 2 }
    }

    // CR 702.40a, 3 players: each copy can choose a new target; here the copies go to different players
    @Test
    fun stormCopiesChooseNewTargets() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Island")
        alice.hand(whisper, volley)
        alice.casts(whisper)
        resolveAll()
        alice.casts(volley, target = "bob")
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }
}
