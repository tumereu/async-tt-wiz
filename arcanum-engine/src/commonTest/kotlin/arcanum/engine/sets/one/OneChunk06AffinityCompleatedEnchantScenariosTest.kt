package arcanum.engine.sets.one

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.fra.loyalty
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 06 in play: affinity for Equipment and losing indestructible after damage (CR 702.41a, 613.1f, 704.5g);
 * compleated planeswalkers' loyalty abilities: a condition on any graveyard (CR 404.1), milling three times X (CR 107.3), a requirement to
 * attack a player (CR 508.1d), a token copy of an exiled card that is exiled at the next end step (CR 707.2, 603.7), a token as big as the
 * loyalty after the cost (CR 606.4), a creature turned into a Treasure (CR 205.1a, 613.1d, f), poison up to nine (CR 122.1f) and damage
 * divided by the greatest power (CR 601.2d); doubling power and toughness each combat (CR 701.10f); Auras with an intervening "if" (CR 603.4),
 * proliferate (CR 701.34a) and a return of the creature card that died (CR 603.10a).
 */
class OneChunk06AffinityCompleatedEnchantScenariosTest {
    private val volley = Cards.invent("Gearbreaker Volley", "{2}{R}", "Instant", """
        Affinity for Equipment (This spell costs {1} less to cast for each Equipment you control.)
        Gearbreaker Volley deals 5 damage to target creature or planeswalker. That permanent loses indestructible until end of turn.
    """)
    private val shiv = Cards.invent("Rivet Shiv", "{1}", "Artifact — Equipment", "Equip {1}")
    private val flail = Cards.invent("Hookchain Flail", "{2}", "Artifact — Equipment", "Equip {2}")
    private val bulwark = Cards.invent("Tarnished Bulwark", "{3}{W}", "Creature — Phyrexian Wall", "Indestructible", "3/5")
    private val orrin = Cards.invent("Orrin, the Perfected Mind", "{2}{U}{U/P}", "Legendary Planeswalker — Jace", """
        Compleated ({U/P} can be paid with {U} or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
        [+1]: Until your next turn, up to one target creature gets -3/-0.
        [−2]: Target player mills three cards. Then if a graveyard has twenty or more cards in it, you draw three cards. Otherwise, you draw a card.
        [−X]: Target player mills three times X cards.
    """, loyalty = "5")
    private val lokka = Cards.invent("Lokka, Bound to Ruin", "{2}{R}{R/G/P}{G}", "Legendary Planeswalker — Lukka", """
        Compleated ({R/G/P} can be paid with {R}, {G}, or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
        [+1]: Add {R}{G}. Spend this mana only to cast creature spells or activate abilities of creatures.
        [−1]: Create a 3/3 green Phyrexian Beast creature token with toxic 1.
        [−4]: Lokka deals X damage divided as you choose among any number of target creatures and/or planeswalkers, where X is the greatest power among creatures you control as you activate this ability.
    """, loyalty = "5")
    private val nahra = Cards.invent("Nahra, the Unforgiving", "{1}{R}{R/W/P}{W}", "Legendary Planeswalker — Nahiri", """
        Compleated ({R/W/P} can be paid with {R}, {W}, or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
        [+1]: Until your next turn, up to one target creature attacks a player each combat if able.
        [+1]: Discard a card, then draw a card.
        [0]: Exile target creature or Equipment card with mana value less than Nahra's loyalty from your graveyard. Create a token that's a copy of it. That token gains haste. Exile it at the beginning of the next end step.
    """, loyalty = "5")
    private val nessa = Cards.invent("Nessa, Ascended Animist", "{3}{G}{G}{G/P}{G/P}", "Legendary Planeswalker — Nissa", """
        Compleated ({G/P} can be paid with {G} or 2 life. For each {G/P} paid with life, this planeswalker enters with two fewer loyalty counters.)
        [+1]: Create an X/X green Phyrexian Horror creature token, where X is Nessa's loyalty.
        [−1]: Destroy target artifact or enchantment.
        [−7]: Until end of turn, creatures you control get +1/+1 for each Forest you control and gain trample.
    """, loyalty = "7")
    private val vrasha = Cards.invent("Vrasha, Betrayal's Sting", "{4}{B}{B/P}", "Legendary Planeswalker — Vraska", """
        Compleated ({B/P} can be paid with {B} or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
        [0]: You draw a card and lose 1 life. Proliferate.
        [−2]: Target creature becomes a Treasure artifact with "{T}, Sacrifice this artifact: Add one mana of any color" and loses all other card types and abilities.
        [−9]: If target player has fewer than nine poison counters, they get a number of poison counters equal to the difference.
    """, loyalty = "6")
    private val zorandel = Cards.invent("Zorandel, Hunger Dominus", "{5}{G}{G}", "Legendary Creature — Phyrexian Horror", """
        Reach
        At the beginning of each combat, double the power and toughness of each creature you control until end of turn.
        {G/P}{G/P}, Sacrifice two other creatures: Put an indestructible counter on Zorandel. ({G/P} can be paid with either {G} or 2 life.)
    """, "4/6")
    private val mantle = Cards.invent("Maze-Thread Mantle", "{2}{G}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, if enchanted creature has toxic, that creature gains hexproof until end of turn.
        Enchanted creature gets +2/+2.
    """)
    private val dose = Cards.invent("Mesmeric Dose", "{1}{U}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature, then proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        Enchanted creature doesn't untap during its controller's untap step.
    """)
    private val communion = Cards.invent("Necrotic Communion", "{1}{B}", "Enchantment — Aura", """
        Enchant creature you control
        Enchanted creature has toxic 2. (Players dealt combat damage by it also get two poison counters.)
        When enchanted creature dies, return that card to the battlefield under your control.
    """)
    private val hound = Cards.invent("Gristle Hound", "{1}{G}", "Creature — Phyrexian Dog", "", "2/2")
    private val pup = Cards.invent("Copperjaw Pup", "{1}{R}", "Creature — Dog", "", "2/2")
    private val shrew = Cards.invent("Oilcap Shrew", "{1}{B}", "Creature — Shrew", "", "2/2")
    private val ogre = Cards.invent("Sporeback Ogre", "{3}{G}", "Creature — Ogre", "", "4/4")
    private val mite = Cards.invent("Ichorlash Mite", "{G}", "Creature — Phyrexian Mite", "Toxic 1", "1/1")
    private val lion = Cards.invent("Rustmane Lion", "{4}{W}", "Creature — Cat", "", "5/5")
    private val wyvern = Cards.invent("Palace Wyvern", "{2}{G}", "Creature — Drake", "Flying", "2/3")
    private val warden = Cards.invent("Furnace Warden", "{2}{R}", "Legendary Planeswalker — Warden", "[+1]: You gain 1 life.", loyalty = "3")
    private val spitebolt = Cards.invent("Spitebolt", "{R}", "Instant", "Spitebolt deals 2 damage to target creature.")

    // CR 702.41a: two Equipment pay {2} of it; CR 613.1f, 704.5g: without indestructible, its lethal damage destroys it
    @Test
    fun volleyCostsLessAndRemovesIndestructible() = scenario {
        alice.battlefield("Mountain", shiv, flail)
        bob.battlefield(bulwark)
        alice.hand(volley)
        alice.casts(volley, target = bulwark)
        resolveAll()
        expect {
            bob.graveyard has bulwark
            alice.graveyard has volley
        }
    }

    // CR 404.1: any player's graveyard counts (carol's twenty cards); CR 608.2c: then three cards drawn
    @Test
    fun orrinDrawsThreeWhenAnyGraveyardHasTwenty() = scenario(players = 3) {
        alice.battlefield(orrin, counters = listOf(CounterKind.Loyalty to 5))
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Plains", "Plains", "Plains", "Plains")
        carol.graveyard(*Array(20) { "Swamp" })
        alice.activates(orrin, ability = 1, target = "bob")
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 3
            alice.hand.size shouldBe 3
        }
        assertEquals(3, loyalty(orrin))
    }

    // CR 608.2c: no graveyard with twenty cards (bob's nineteen after milling): one card
    @Test
    fun orrinDrawsOneOtherwise() = scenario(players = 3) {
        alice.battlefield(orrin, counters = listOf(CounterKind.Loyalty to 5))
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Plains", "Plains", "Plains", "Plains")
        bob.graveyard(*Array(16) { "Swamp" })
        alice.activates(orrin, ability = 1, target = "bob")
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 19
            alice.hand.size shouldBe 1
        }
    }

    // CR 107.3, 606.4: X 2 removes two loyalty counters; the player mills six
    @Test
    fun orrinMillsThreeTimesX() = scenario {
        alice.battlefield(orrin, counters = listOf(CounterKind.Loyalty to 5))
        bob.library(*Array(8) { "Plains" })
        alice.activates(orrin, ability = 2, target = "bob", x = 2)
        resolveAll()
        expect { bob.graveyard.size shouldBe 6 }
        assertEquals(3, loyalty(orrin))
    }

    // CR 601.2d, 602.2b: X is the greatest power among her controller's creatures (4), divided among two targets
    @Test
    fun lokkaDividesTheGreatestPower() = scenario {
        alice.battlefield(lokka, counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield(ogre, hound)
        bob.battlefield(pup, shrew)
        alice.activates(lokka, ability = 2, targets = listOf(pup, shrew))
        assertEquals(4, decision<Decision.Divide>().amount)
        answer(Answer.Division(listOf(Share(target(pup), 2), Share(target(shrew), 2))))
        resolveAll()
        expect { bob.graveyard.size shouldBe 2 }
    }

    // CR 508.1d: the creature must attack a player; attacking the planeswalker obeys nothing, so it isn't allowed while attacking bob is
    @Test
    fun nahraMakesACreatureAttackAPlayer() = scenario {
        alice.battlefield(nahra, counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield(hound)
        bob.battlefield(warden, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(nahra, ability = 0, target = hound)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(hound, defender = warden) }
        assertFailsWith<IllegalArgumentException> { alice.attacks() }
        alice.attacks(hound, defender = "bob")
        expect { hound.tapped shouldBe true }
    }

    // CR 707.2: the token copies the exiled card and has haste; CR 603.7: it's exiled at the next end step; mana value 5 isn't less than 5
    @Test
    fun nahraReanimatesACopyForATurn() = scenario {
        alice.battlefield(nahra, counters = listOf(CounterKind.Loyalty to 5))
        alice.graveyard(pup, lion)
        alice.activates(nahra, ability = 2)
        val options = decision<Decision.Targets>().options
        assertTrue(target(pup) in options)
        assertTrue(target(lion) !in options)
        targets(pup)
        resolveAll()
        val token = tokens(pup).single()
        expect {
            alice.exile has pup
            CardRef(token, "copy") hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(tokens(pup).isEmpty())
    }

    // CR 606.4: the +1 is paid first, so X is eight
    @Test
    fun nessaMakesATokenAsBigAsHerLoyalty() = scenario {
        alice.battlefield(nessa, counters = listOf(CounterKind.Loyalty to 7))
        alice.activates(nessa, ability = 0)
        resolveAll()
        expect {
            "Phyrexian Horror Token".power shouldBe 8
            "Phyrexian Horror Token".toughness shouldBe 8
        }
    }

    // CR 613.4c: +1/+1 for each of three Forests, and trample
    @Test
    fun nessaPumpsForEachForest() = scenario {
        alice.battlefield(nessa, counters = listOf(CounterKind.Loyalty to 7))
        alice.battlefield("Forest", "Forest", "Forest", hound)
        alice.activates(nessa, ability = 2)
        resolveAll()
        expect {
            hound.power shouldBe 5
            hound.toughness shouldBe 5
            hound hasKeyword Keyword.Trample.kind
        }
    }

    // CR 205.1a, 613.1d, f: only a Treasure artifact, its flying gone, its color kept
    @Test
    fun vrashaTurnsACreatureIntoATreasure() = scenario {
        alice.battlefield(vrasha, counters = listOf(CounterKind.Loyalty to 6))
        bob.battlefield(wyvern)
        alice.activates(vrasha, ability = 1, target = wyvern)
        resolveAll()
        expect {
            wyvern.types shouldBe listOf(CardType.Artifact)
            wyvern.subtypes shouldBe listOf(Subtype("Treasure"))
            wyvern lacksKeyword Keyword.Flying.kind
            wyvern.colors shouldBe listOf(arcanum.api.Color.Green)
        }
    }

    // CR 122.1f: three poison counters become nine; in a three-player game only the target gets them
    @Test
    fun vrashaGivesPoisonUpToNine() = scenario(players = 3) {
        alice.battlefield(vrasha, counters = listOf(CounterKind.Loyalty to 9))
        bob.counters(CounterKind.Poison, 3)
        carol.counters(CounterKind.Poison, 3)
        alice.activates(vrasha, ability = 2, target = "bob")
        resolveAll()
        assertEquals(9, bob.counters(CounterKind.Poison))
        assertEquals(3, carol.counters(CounterKind.Poison))
    }

    // CR 701.10f, 507.1: at the beginning of combat each creature its controller controls doubles
    @Test
    fun zorandelDoublesEachCombat() = scenario {
        alice.battlefield(zorandel, hound)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            hound.power shouldBe 4
            hound.toughness shouldBe 4
            zorandel.power shouldBe 8
            zorandel.toughness shouldBe 12
        }
    }

    // CR 603.4: a creature with toxic gains hexproof; one without doesn't (the ability doesn't trigger)
    @Test
    fun mantleGivesHexproofOnlyToAToxicCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", mite, hound)
        alice.hand(mantle, "Maze-Thread Mantle")
        alice.casts(mantle, target = mite)
        resolveAll()
        alice.casts("Maze-Thread Mantle", target = hound)
        resolveAll()
        expect {
            mite hasKeyword Keyword.Hexproof.kind
            mite.power shouldBe 3
            hound lacksKeyword Keyword.Hexproof.kind
            hound.power shouldBe 4
        }
    }

    // CR 701.34a: tapped, then proliferate adds a +1/+1 counter
    @Test
    fun doseTapsThenProliferates() = scenario {
        alice.battlefield("Island", "Island", "Island")
        bob.battlefield(ogre, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.hand(dose)
        alice.casts(dose, target = ogre)
        resolveAll()
        proliferating(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 603.10a: the creature card that died returns; the Aura stays in the graveyard; CR 702.164a: toxic 2 while enchanted
    @Test
    fun communionReturnsTheCreatureThatDied() = scenario {
        alice.battlefield("Mountain", hound, communion)
        attach(communion, hound)
        alice.hand(spitebolt)
        expect { hound hasKeyword Keyword.Toxic.Kind }
        alice.casts(spitebolt, target = hound)
        resolveAll()
        expect {
            alice.battlefield has hound
            alice.graveyard has communion
            hound lacksKeyword Keyword.Toxic.Kind
        }
    }

    private fun Scenario.proliferating(vararg names: String) {
        assertEquals("proliferate", decision<Decision.Targets>().slot)
        targets(*names)
    }
}
