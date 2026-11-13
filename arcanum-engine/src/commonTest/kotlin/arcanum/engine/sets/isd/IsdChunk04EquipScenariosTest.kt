package arcanum.engine.sets.isd

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** ISD chunk 04: Equipment the engine hadn't played: combat damage doubled by and to the equipped creature, a bonus counted from the
 * graveyard, bonuses only for a Human, a defending player revealing until a land on attack, a trigger on blocking or being blocked by
 * a Vampire that destroys it, and blocking restrictions by creature type (CR 301.5, 702.6). Invented names carry the oracle text of
 * the real cards. */
class IsdChunk04EquipScenariosTest {
    private val scourge = Cards.invent("Zealot's Scourge", "{2}", "Artifact — Equipment", """
        If equipped creature would deal combat damage, it deals double that damage instead.
        If another creature would deal combat damage to equipped creature, it deals double that damage to equipped creature instead.
        Equip {2}
    """)
    private val glaive = Cards.invent("Spellscribe's Glaive", "{2}", "Artifact — Equipment", """
        Equipped creature has first strike and gets +X/+0, where X is the number of instant and sorcery cards in your graveyard.
        Equip {2}
    """)
    private val hayfork = Cards.invent("Honed Hayfork", "{2}", "Artifact — Equipment", """
        Equipped creature has first strike.
        As long as equipped creature is a Human, it gets +1/+1.
        Equip {1}
    """)
    private val dirk = Cards.invent("Silverchased Dirk", "{1}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        As long as equipped creature is a Human, it gets an additional +1/+0.
        Equip {2}
    """)
    private val knife = Cards.invent("Skullbore Knife", "{3}", "Artifact — Equipment", """
        Whenever equipped creature attacks, defending player reveals cards from the top of their library until they reveal a land card. The creature gets +1/+0 until end of turn for each card revealed this way. That player puts the revealed cards into their graveyard.
        Equip {2}
    """)
    private val spike = Cards.invent("Ashwood Spike", "{2}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        Whenever equipped creature blocks or becomes blocked by a Vampire, destroy that creature. It can't be regenerated.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val brand = Cards.invent("Flaring Brand", "{1}", "Artifact — Equipment", """
        Equipped creature can't be blocked by Vampires or Zombies.
        Equipped creature has "{T}, Sacrifice Flaring Brand: Flaring Brand deals 2 damage to any target."
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val hatchet = Cards.invent("Slaughterman's Hatchet", "{3}", "Artifact — Equipment", """
        Equipped creature gets +3/+0.
        As long as equipped creature is a Human, it has lifelink.
        Equip {3}
    """)
    private val watchman = Cards.invent("Hamlet Watchman", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val prowler = Cards.invent("Nightfang Prowler", "{2}{B}", "Creature — Vampire", "", "3/3")
    private val stalker = Cards.invent("Gloomfang Stalker", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val shambler = Cards.invent("Crypt Shambler", "{2}{B}", "Creature — Zombie", "", "2/5")
    private val insight = Cards.invent("Moonlit Insight", "{U}", "Instant", "Draw a card.")
    private val kindling = Cards.invent("Lantern Kindling", "{R}", "Sorcery", "Draw a card.")

    // CR 614.1a, 510.2: the 2/5 deals 4 to the 3/3 and is dealt 6: both die, though neither would without the Scourge
    @Test
    fun scourgeDoublesCombatDamageByAndToTheEquippedCreature() = scenario {
        alice.battlefield(shambler, scourge)
        attach(scourge, shambler)
        bob.battlefield(prowler)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shambler)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(prowler to shambler)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.graveyard has shambler
            bob.graveyard has prowler
        }
    }

    // CR 614.1a: unblocked, the 2/5 deals 4 combat damage to the defending player
    @Test
    fun scourgeDoublesCombatDamageToAPlayer() = scenario {
        alice.battlefield(shambler, scourge)
        attach(scourge, shambler)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shambler)
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(16, bob.life)
    }

    // CR 613.4c, 208.1: an instant and a sorcery in Alice's graveyard: +2/+0 (the creature card doesn't count); CR 702.7a
    @Test
    fun glaiveCountsInstantAndSorceryCards() = scenario {
        alice.battlefield(watchman, glaive)
        attach(glaive, watchman)
        alice.graveyard(insight, kindling, prowler)
        bob.graveyard(stalker)
        expect {
            watchman.power shouldBe 4
            watchman.toughness shouldBe 2
            watchman hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 611.3a, 613.4c: the Human gets first strike and +1/+1 from the Hayfork; the Zombie gets only the Dirk's +2/+0
    @Test
    fun hayforkOnAHumanAndDirkOnAZombie() = scenario {
        alice.battlefield(watchman, shambler, hayfork, dirk)
        attach(hayfork, watchman)
        attach(dirk, shambler)
        expect {
            watchman.power shouldBe 3
            watchman.toughness shouldBe 3
            watchman hasKeyword Keyword.FirstStrike.kind
            shambler.power shouldBe 4
            shambler.toughness shouldBe 5
        }
    }

    // CR 611.3a, 613.4c: the Human gets the Dirk's +2/+0 and the additional +1/+0; the Zombie gets only first strike
    @Test
    fun dirkOnAHumanAndHayforkOnAZombie() = scenario {
        alice.battlefield(watchman, shambler, hayfork, dirk)
        attach(dirk, watchman)
        attach(hayfork, shambler)
        expect {
            watchman.power shouldBe 5
            watchman.toughness shouldBe 2
            shambler.power shouldBe 2
            shambler.toughness shouldBe 5
            shambler hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 506.2, 508.1m, 701.20a: Carol, the player attacked, reveals two creature cards and a land: +3/+0, all three to her graveyard;
    // Bob's library isn't touched
    @Test
    fun knifeMakesTheDefendingPlayerRevealUntilALand() = scenario(players = 3) {
        alice.battlefield(watchman, knife)
        attach(knife, watchman)
        bob.library("Plains")
        carol.library(prowler, shambler, "Forest", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(watchman, defender = "carol")
        resolveAll()
        expect {
            watchman.power shouldBe 5
            carol.graveyard has prowler
            carol.graveyard has shambler
            carol.graveyard has "Forest"
            carol.library.names shouldBe listOf("Island")
            bob.library.names shouldBe listOf("Plains")
        }
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(15, carol.life)
    }

    // CR 509.3d, 701.19c: blocked by two Vampires and a Zombie: a trigger for each Vampire destroys it; the Zombie stays
    @Test
    fun spikeDestroysEachVampireThatBlocksIt() = scenario(players = 3) {
        alice.battlefield(watchman, spike)
        attach(spike, watchman)
        bob.battlefield(prowler, stalker, shambler)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(watchman, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(prowler to watchman, stalker to watchman, shambler to watchman)
        resolveAll()
        expect {
            bob.graveyard has prowler
            bob.graveyard has stalker
            bob.graveyard lacks shambler
            alice.graveyard lacks watchman
        }
    }

    // CR 509.3b: the equipped creature blocks an attacking Vampire: that Vampire is destroyed, not the blocker
    @Test
    fun spikeDestroysTheVampireItBlocks() = scenario {
        alice.battlefield(watchman, spike)
        attach(spike, watchman)
        bob.battlefield(prowler)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(prowler)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(watchman to prowler)
        resolveAll()
        expect {
            bob.graveyard has prowler
            alice.graveyard lacks watchman
        }
    }

    // CR 509.1b: neither the Vampire nor the Zombie can block the equipped creature; the Human can
    @Test
    fun brandStopsVampiresAndZombiesFromBlocking() = scenario {
        alice.battlefield(stalker, brand)
        attach(brand, stalker)
        bob.battlefield(prowler, shambler, watchman)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stalker)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(prowler to stalker) }
        assertFailsWith<Throwable> { bob.blocks(shambler to stalker) }
        bob.blocks(watchman to stalker)
    }

    // CR 702.15b, 611.3a: the Human deals 5 and Alice gains 5; on the Zombie the Hatchet gives no lifelink
    @Test
    fun hatchetGivesLifelinkOnlyToAHuman() = scenario {
        alice.battlefield(watchman, hatchet)
        attach(hatchet, watchman)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(watchman)
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(15, bob.life)
        assertEquals(25, alice.life)
    }

    // CR 611.3a: not a Human, so no lifelink
    @Test
    fun hatchetOnAZombieGivesNoLifelink() = scenario {
        alice.battlefield(shambler, hatchet)
        attach(hatchet, shambler)
        expect {
            shambler.power shouldBe 5
            shambler lacksKeyword Keyword.Lifelink.kind
        }
    }
}
