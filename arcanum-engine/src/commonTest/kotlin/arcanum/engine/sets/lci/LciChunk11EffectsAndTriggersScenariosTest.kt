package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dgm.payIfAsked
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** LCI chunk 11 played: an each-opponent punisher for three players, discovering again for the same value, a base power borrowed from an
 * entering Dinosaur, a Cave land's activation count, descends counted, a reflexive Dinosaur strike, a graveyard return that is a 1/1 flyer
 * as it enters, a reveal-or-Dinosaur life gain, revealing that many and putting Dinosaurs onto the battlefield, the player with the most
 * life, power above base power, and a creature exiled as a craft material. Invented names carry the oracle text. */
class LciChunk11EffectsAndTriggersScenariosTest {
    private val tonguewitch = Cards.invent("Smoldering Tonguewitch", "{B}{R}", "Legendary Creature — Goblin Warlock", """
        Deathtouch
        At the beginning of your end step, if you descended this turn, each opponent may discard a card or sacrifice a permanent of their choice. Smoldering Tonguewitch deals 3 damage to each opponent who didn't. (You descended if a permanent card was put into your graveyard from anywhere.)
    """, "2/2")
    private val keeper = Cards.invent("Keeper of the Second Dawn", "{3}{R}", "Creature — Human Artificer",
        "Whenever you discover, discover again for the same value. This ability triggers only once each turn.", "3/3")
    private val hatchling = Cards.invent("Hotheaded Hatchling", "{1}{R}", "Creature — Dinosaur", """
        Trample
        Whenever another Dinosaur you control enters, you may have this creature's base power become equal to that creature's power until end of turn.
    """, "3/2")
    private val gullet = Cards.invent("Hollow Gullet", null, "Land — Cave", """
        {T}: Add {C}.
        {2}: This land becomes a 3/3 Elemental creature until end of turn. It's still a Cave land. Activate only if the number of other Caves you control plus the number of Cave cards in your graveyard is three or greater.
    """)
    private val sporemonarch = Cards.invent("The Sporemonarch", "{1}{B}{G}", "Legendary Creature — Elder Fungus", """
        Trample
        The Sporemonarch's power and toughness are each equal to the number of creatures you control that are Fungi and/or Saprolings.
        At the beginning of your end step, create X 1/1 black Fungus creature tokens with "This token can't block," where X is the number of times you descended this turn. (You descend each time a permanent card is put into your graveyard from anywhere.)
    """, "*/*")
    private val quetzalcrest = Cards.invent("Quetzalcrest, Firstborn of Ixa", "{R}{G}", "Legendary Creature — Dinosaur", """
        Haste
        When Quetzalcrest enters, you may pay {2}. When you do, target Dinosaur you control deals damage equal to its power to another target creature.
    """, "2/3")
    private val rousing = Cards.invent("Grandmother's Rousing", "{X}{3}{W}", "Sorcery",
        "Return target artifact or non-Aura enchantment card from your graveyard to the battlefield with X additional +1/+1 counters on it. It's a 1/1 Spirit creature with flying in addition to its other types.")
    private val paladin = Cards.invent("Tidewatch Paladin", "{3}{B}{B}", "Creature — Vampire Knight",
        "Whenever this creature enters or attacks, return up to one target Vampire card from your graveyard to the battlefield with a finality counter on it. You lose life equal to its mana value. (If a creature with a finality counter on it would die, exile it instead.)",
        "5/4")
    private val kinfriend = Cards.invent("Plated Kinfriend", "{2}{G}", "Creature — Dinosaur",
        "When this creature enters, you may reveal a Dinosaur card from your hand. If you do or if you control another Dinosaur, you gain 3 life.", "3/3")
    private val ixtlan = Cards.invent("Ixtlan, Dawn's Avatar", "{5}{R}{G}{W}", "Legendary Creature — Dinosaur Avatar", """
        Vigilance, trample, haste
        Whenever Ixtlan deals combat damage to a player, reveal that many cards from the top of your library. Put any number of Dinosaur creature cards from among them onto the battlefield and the rest on the bottom of your library in a random order.
    """, "7/6")
    private val herald = Cards.invent("Herald of the Ruin", "{2}{B}", "Creature — Vampire Cleric", """
        Deathtouch
        Whenever this creature attacks the player with the most life or tied for most life, create a 1/1 white Vampire creature token with lifelink.
        Whenever this creature attacks while you have the most life or are tied for most life, you draw a card and you lose 1 life.
    """, "2/4")
    private val swiftblade = Cards.invent("Malamet Swiftblade", "{1}{G}{W}", "Legendary Creature — Cat Warrior", """
        Your opponents can't cast spells during your turn.
        Whenever one or more creatures you control each with power greater than its base power deals combat damage to a player, draw a card.
    """, "3/3")
    private val gnome = Cards.invent("Bazaar Gnome", "{W}", "Artifact Creature — Gnome", """
        When this creature dies, you gain 1 life and draw a card.
        When this creature is exiled from the battlefield while you're activating a craft ability, you gain 1 life and draw a card.
    """, "0/3")
    private val sextant = Cards.inventFaces("Transform",
        face("Gloomtide Sextant", "{1}", "Artifact",
            "Craft with artifact {1} ({1}, Exile this artifact, Exile another artifact you control or an artifact card from your graveyard: Return this card transformed under its owner's control. Craft only as a sorcery.)",
            null, null, null),
        face("Gloomtide Orrery", null, "Artifact", "{T}: Add {C}{C}.", null, null, null))

    private val zap = Cards.invent("Cavern Zap", "{R}", "Instant", "Cavern Zap deals 2 damage to target creature.")
    private val runt = Cards.invent("Cliffhorn Runt", "{1}{G}", "Creature — Dinosaur", "", "2/2")
    private val brute = Cards.invent("Sunscale Brute", "{3}{G}", "Creature — Dinosaur", "", "6/4")
    private val oddment = Cards.invent("Mire Oddment", "{1}", "Artifact", "")
    private val grub = Cards.invent("Shalefoot Grub", "{G}", "Creature — Insect", "", "1/1")
    private val cave = Cards.invent("Dripstone Cave", null, "Land — Cave", "{T}: Add {R}.")
    private val shoot = Cards.invent("Saproling Shoot", "{G}", "Creature — Saproling", "", "1/1")
    private val mote = Cards.invent("Lantern Mote", "{2}", "Artifact Creature — Construct", "", "2/2")
    private val thrall = Cards.invent("Brinecourt Thrall", "{1}{B}{B}", "Creature — Vampire", "", "3/3")
    private val pebble = Cards.invent("Quarry Pebble", "{1}", "Artifact", "")
    private val sparkSix = Cards.invent("Sixfold Spark", "{2}{R}", "Sorcery", "Discover 3. (Exile cards from the top of your library until you exile a nonland card with mana value 3 or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)")
    private val pump = Cards.invent("Brawn Surge", "{G}", "Instant", "Target creature gets +2/+0 until end of turn.")

    // ---- Smoldering Tonguewitch

    // CR 700.11: a creature card of hers died this turn; CR 101.4: Bob discards, Carol gives nothing and takes 3 (CR 120.3)
    @Test
    fun tonguewitchHitsTheOpponentWhoGaveNothing() = scenario(players = 3) {
        alice.battlefield(tonguewitch, grub, "Mountain")
        alice.hand(zap)
        bob.hand("Island")
        carol.hand("Forest")
        alice.casts(zap, target = grub)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        bob.answers(Answer.YesNo(true))
        carol.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.graveyard has "Island"
            bob.life shouldBe 20
            carol.life shouldBe 17
            carol.hand has "Forest"
        }
    }

    // CR 603.4: without a descend this turn it doesn't trigger
    @Test
    fun tonguewitchDoesNothingWithoutADescend() = scenario(players = 3) {
        alice.battlefield(tonguewitch)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // ---- Keeper of the Second Dawn

    // CR 701.57a: discover 3 again; CR 603.2h: only once each turn, so the second discover doesn't trigger it again
    @Test
    fun keeperDiscoversAgainForTheSameValue() = scenario {
        alice.battlefield(keeper, "Mountain", "Mountain", "Mountain")
        alice.hand(sparkSix)
        alice.library(grub, brute, runt, "Forest")
        alice.casts(sparkSix)
        resolve()
        // the first discover 3 finds the Grub: put it into the hand
        alice.answers(Answer.YesNo(false))
        resolveAll()
        // the second discover 3 passes over the 4-mana Brute and finds the Runt
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.hand has grub
            alice.hand has runt
            assertEquals(0, state.stack.size)
        }
    }

    // ---- Hotheaded Hatchling

    // CR 613.4b: base power 6 from the entering Brute until end of turn; its toughness stays 2
    @Test
    fun hatchlingTakesTheEnteringDinosaursPower() = scenario {
        alice.battlefield(hatchling, "Forest", "Forest", "Forest", "Forest")
        alice.hand(brute)
        alice.library("Forest")
        bob.library("Island")
        alice.casts(brute)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            hatchling.power shouldBe 6
            hatchling.toughness shouldBe 2
        }
        // CR 514.2: until end of turn
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { hatchling.power shouldBe 3 }
    }

    // ---- Hollow Gullet

    // CR 602.5b: two other Caves and a Cave card in the graveyard make three: it becomes a 3/3 Elemental creature and stays a Cave land
    // (CR 205.1b)
    @Test
    fun gulletAnimatesWithThreeCaves() = scenario {
        alice.battlefield(gullet, cave, cave, "Mountain", "Mountain")
        alice.graveyard(cave)
        alice.activates(gullet, ability = 1)
        resolveAll()
        val id = card(gullet)
        val c = view()[id]!!.characteristics!!
        assertEquals(3, c.power)
        assertTrue(Subtype("Cave") in c.subtypes && Subtype("Elemental") in c.subtypes)
        assertTrue(arcanum.api.CardType.Land in c.types && arcanum.api.CardType.Creature in c.types)
    }

    // CR 602.5b: itself doesn't count ("other"): one other Cave and one in the graveyard are two
    @Test
    fun gulletCantAnimateWithTwo() = scenario {
        alice.battlefield(gullet, cave, "Mountain", "Mountain")
        alice.graveyard(cave)
        assertFailsWith<IllegalArgumentException> { alice.activates(gullet, ability = 1) }
    }

    // ---- The Sporemonarch

    // CR 604.3: a Fungus (itself) and a Saproling; CR 700.11: two permanent cards put into her graveyard this turn make two tokens
    @Test
    fun sporemonarchCountsFungiAndSaprolingsAndDescends() = scenario {
        alice.battlefield(sporemonarch, shoot, grub, "Mountain", "Mountain")
        alice.hand(zap)
        alice.library(oddment, "Forest")
        alice.casts(zap, target = grub)
        resolveAll()
        expect { sporemonarch.power shouldBe 2 }
        move(oddment, Zone.Graveyard)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Fungus Token" } shouldBe 2
            sporemonarch.power shouldBe 4
        }
    }

    // ---- Quetzalcrest, Firstborn of Ixa

    // CR 603.12: paid, the reflexive trigger targets a Dinosaur and another creature; CR 115.3: not the same creature twice
    @Test
    fun quetzalcrestsDinosaurStrikesAnotherCreature() = scenario {
        alice.battlefield(brute, "Mountain", "Forest", "Forest", "Forest")
        alice.hand(quetzalcrest)
        bob.battlefield(thrall)
        alice.casts(quetzalcrest)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        payIfAsked()
        alice.answers(Answer.Targets(listOf(target(brute))))
        val second = decision<Decision.Targets>()
        assertTrue(second.options.none { it == target(brute) })
        alice.answers(Answer.Targets(listOf(target(thrall))))
        resolveAll()
        expect { bob.graveyard has thrall }
    }

    // ---- Grandmother's Rousing

    // CR 611.2e: the artifact is a 1/1 Spirit creature with flying as it enters, plus X = 2 +1/+1 counters (CR 122.6)
    @Test
    fun rousingReturnsAFlyingSpirit() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(pebble)
        alice.hand(rousing)
        alice.casts(rousing, target = pebble, x = 2)
        resolveAll()
        expect {
            pebble.zone shouldBe Zone.Battlefield
            pebble.power shouldBe 3
            pebble.toughness shouldBe 3
            pebble hasKeyword Keyword.Flying.kind
            assertTrue(Subtype("Spirit") in pebble.subtypes)
        }
    }

    // ---- Tidewatch Paladin

    // CR 122.6: back with a finality counter; she loses life equal to its mana value
    @Test
    fun paladinReturnsAVampireAndCostsItsManaValue() = scenario {
        alice.battlefield(*Array(5) { "Swamp" })
        alice.hand(paladin)
        alice.graveyard(thrall)
        alice.casts(paladin)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(thrall))))
        resolveAll()
        expect {
            thrall.zone shouldBe Zone.Battlefield
            thrall.counters(CounterKind.Finality) shouldBe 1
            alice.life shouldBe 17
        }
    }

    // ---- Plated Kinfriend

    // CR 701.20a: revealing a Dinosaur card gains the life
    @Test
    fun kinfriendGainsLifeForARevealedDinosaur() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(kinfriend, runt)
        alice.casts(kinfriend)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // another Dinosaur gains it without a reveal; with neither, none
    @Test
    fun kinfriendGainsLifeForAnotherDinosaurOnly() = scenario {
        alice.battlefield(runt, "Forest", "Forest", "Forest")
        alice.hand(kinfriend)
        alice.casts(kinfriend)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun kinfriendGainsNothingAlone() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(kinfriend, grub)
        alice.casts(kinfriend)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    // ---- Ixtlan, Dawn's Avatar

    // CR 510.2: 7 damage reveals 7; she puts both Dinosaur creature cards onto the battlefield, the rest goes to the bottom
    @Test
    fun ixtlanPutsRevealedDinosaursOntoTheBattlefield() = scenario {
        alice.battlefield(ixtlan)
        alice.library(runt, "Forest", brute, grub, "Forest", "Forest", "Forest", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ixtlan)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses(runt, brute)
        resolveAll()
        expect {
            alice.battlefield has runt
            alice.battlefield has brute
            bob.life shouldBe 13
            alice.library has grub
            alice.library.size shouldBe 6
        }
    }

    // ---- Herald of the Ruin

    // CR 702.105a: attacking Carol, who has the most life, makes a lifelink Vampire; Alice isn't tied for most, so no card
    @Test
    fun heraldAttackingTheMostLifeMakesAVampire() = scenario(players = 3) {
        alice.battlefield(herald)
        alice.library("Swamp")
        alice.life = 18
        carol.life = 25
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(herald, defender = "carol")
        resolveAll()
        expect {
            alice.battlefield has "Vampire Token"
            alice.hand.size shouldBe 0
            alice.life shouldBe 18
        }
    }

    // CR 603.4: tied for the most life (20 each), she draws and loses 1; attacking Bob (also tied) makes the token too
    @Test
    fun heraldWhileTiedForMostDrawsAndLoses() = scenario(players = 3) {
        alice.battlefield(herald)
        alice.library("Swamp")
        carol.life = 12
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(herald, defender = "bob")
        resolveAll()
        expect {
            alice.hand has "Swamp"
            alice.life shouldBe 19
            alice.battlefield has "Vampire Token"
        }
    }

    // ---- Malamet Swiftblade

    // CR 208.4b: a pumped creature's damage draws; one draw for the batch (CR 603.2c)
    @Test
    fun swiftbladeDrawsForAPumpedAttacker() = scenario {
        alice.battlefield(swiftblade, runt, "Forest")
        alice.hand(pump)
        alice.library("Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(swiftblade, runt)
        alice.casts(pump, target = runt)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // no attacker above its base power: nothing
    @Test
    fun swiftbladeDoesNothingUnpumped() = scenario {
        alice.battlefield(swiftblade, runt)
        alice.library("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(swiftblade, runt)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // ---- Bazaar Gnome

    // CR 702.167a, 602.2b: exiled as a craft material while she activates the craft ability (it's an artifact): 1 life and a card
    @Test
    fun gnomeExiledToCraftGainsAndDraws() = scenario {
        alice.battlefield(sextant, gnome, "Plains")
        alice.library("Plains")
        alice.activates(sextant, choosing = listOf(gnome))
        resolveAll()
        expect {
            alice.exile has gnome
            alice.life shouldBe 21
            alice.hand has "Plains"
        }
    }

    // CR 603.6c: exiled another way, it doesn't trigger
    @Test
    fun gnomeExiledOtherwiseDoesNothing() = scenario {
        alice.battlefield(gnome)
        alice.library("Plains")
        move(gnome, Zone.Exile)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }
}
