package arcanum.engine.sets.woe

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * WOE chunk 10: the invented names carry the oracle text of Gruff Triplets, Johann, Apprentice Sorcerer, Eerie Interference, Virtue of
 * Persistence, Lich-Knights' Conquest, Malevolent Witchkite, Eriette of the Charmed Apple, Faerie Slumber Party, Skewer Slinger, Aquatic
 * Alchemist, Questing Druid, Imodane, the Pyrohammer and Sharae of Numbing Depths.
 */
class WoeChunk10EffectsAndTriggersScenariosTest {
    private val twins = Cards.invent("Surly Twins", "{3}{G}{G}{G}", "Creature — Satyr Warrior", """
        Trample
        When this creature enters, if it isn't a token, create two tokens that are copies of it.
        When this creature dies, put a number of +1/+1 counters equal to its power on each creature you control named Surly Twins.
    """, "3/3")
    private val orsolya = Cards.invent("Orsolya, Tome Apprentice", "{2}{U}{R}", "Legendary Creature — Human Wizard Sorcerer", """
        You may look at the top card of your library any time.
        Once each turn, you may cast an instant or sorcery spell from the top of your library. (You still pay its costs. Timing rules still apply.)
    """, "2/5")
    private val deflection = Cards.invent("Uncanny Deflection", "{2}{W}", "Instant",
        "Prevent all damage that would be dealt to you and creatures you control this turn by creatures.")
    private val creed = Cards.inventFaces("Adventure",
        face("Creed of Tenacity", "{5}{B}{B}", "Enchantment",
            "At the beginning of your upkeep, put target creature card from a graveyard onto the battlefield under your control.", null, null, null),
        face("Bogmire Sneer", "{1}{B}", "Sorcery — Adventure", "Target creature gets -3/-3 until end of turn. You gain 2 life.", null, null, null))
    private val triumph = Cards.invent("Grave-Riders' Triumph", "{4}{B}", "Sorcery",
        "Sacrifice any number of artifacts, enchantments, and/or tokens. Return that many creature cards from your graveyard to the battlefield.")
    private val hexwing = Cards.invent("Spiteful Hexwing", "{4}{B}{B}", "Creature — Dragon Warlock", """
        Flying
        When this creature enters, sacrifice any number of artifacts, enchantments, and/or tokens, then draw that many cards.
    """, "5/4")
    private val marisol = Cards.invent("Marisol of the Gilded Pear", "{1}{W}{B}", "Legendary Creature — Human Warlock", """
        Each creature that's enchanted by an Aura you control can't attack you or planeswalkers you control.
        At the beginning of your end step, each opponent loses X life and you gain X life, where X is the number of Auras you control.
    """, "2/4")
    private val gala = Cards.invent("Pixie Nap Gala", "{4}{U}{U}", "Sorcery",
        "Return all creatures to their owners' hands. For each opponent who controlled a creature returned this way, you create two 1/1 blue " +
            "Faerie creature tokens with flying and \"This token can block only creatures with flying.\"")
    private val hurler = Cards.invent("Spit-Roast Hurler", "{1}{R}", "Creature — Dwarf Knight", """
        Reach
        Whenever this creature blocks or becomes blocked by a creature, this creature deals 1 damage to that creature.
    """, "1/3")
    private val brewer = Cards.inventFaces("Adventure",
        face("Tidepool Brewer", "{1}{U}", "Creature — Elemental",
            "Whenever you cast your first instant or sorcery spell each turn, this creature gets +2/+0 until end of turn.", "1/3", null, null),
        face("Froth Upward", "{2}{U}", "Sorcery — Adventure",
            "Put target instant or sorcery card from your graveyard on top of your library. (Then exile this card. You may cast the creature later from exile.)",
            null, null, null))
    private val druid = Cards.inventFaces("Adventure",
        face("Roving Druid", "{1}{G}", "Creature — Human Druid",
            "Whenever you cast a spell that's white, blue, black, or red, put a +1/+1 counter on this creature.", "1/1", null, null),
        face("Hunt the Quarry", "{1}{R}", "Instant — Adventure",
            "Exile the top two cards of your library. Until your next end step, you may play those cards.", null, null, null))
    private val bertrand = Cards.invent("Bertrand, the Flamebrand", "{2}{R}{R}", "Legendary Creature — Human Knight",
        "Whenever an instant or sorcery spell you control that targets only a single creature deals damage to that creature, Bertrand deals " +
            "that much damage to each opponent.", "4/4")
    private val ondine = Cards.invent("Ondine of Stilling Tides", "{2}{W}{U}", "Legendary Creature — Merfolk Wizard", """
        When Ondine enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Whenever you tap one or more untapped creatures your opponents control, draw a card. This ability triggers only once each turn.
    """, "2/3")

    private val jolt = Cards.invent("Twinbane Jolt", "{R}", "Instant", "Twinbane Jolt deals 3 damage to target creature.")
    private val hex = Cards.invent("Sparkspit Hex", "{R}", "Instant", "Sparkspit Hex deals 2 damage to any target.")
    private val flick = Cards.invent("Cinder Flick", "{R}", "Instant", "Cinder Flick deals 1 damage to any target.")
    private val ox = Cards.invent("Meadow Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val hare = Cards.invent("Glade Hare", "{G}", "Creature — Rabbit", "", "1/1")
    private val squire = Cards.invent("Hedge Squire", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val brute = Cards.invent("Moorland Brute", "{3}{B}", "Creature — Ogre", "", "4/4")
    private val locket = Cards.invent("Tarnished Locket", "{1}", "Artifact", "")
    private val charm = Cards.invent("Faded Charm", "{1}{W}", "Enchantment", "")
    private val shackle = Cards.invent("Thorny Shackle", "{W}", "Enchantment — Aura", "Enchant creature")
    private val gust = Cards.invent("Rimebind Gust", "{U}", "Instant", "Tap up to two target creatures.")

    // CR 603.4, 707.2: the card makes two copies; the copies are tokens, so their own enters ability does nothing
    @Test
    fun surlyTwinsMakesTwoCopiesOnlyFromTheCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(twins)
        alice.casts(twins)
        resolveAll()
        assertEquals(2, tokens(twins).size)
        assertTrue(state.stack.isEmpty())
    }

    // CR 700.4, 201.2, 608.2h: when the card dies, each other creature named Surly Twins gets counters equal to its last power (3)
    @Test
    fun surlyTwinsDyingGrowsTheOthersNamedLikeIt() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Mountain", hare)
        alice.hand(twins, jolt)
        alice.casts(twins)
        resolveAll()
        alice.casts(jolt, target = twins)
        resolveAll()
        val copies = tokens(twins)
        assertEquals(2, copies.size)
        copies.forEach { assertEquals(6, view()[it]!!.characteristics!!.power) }
        expect { hare.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 601.3e: one instant from the top of the library each turn, not a second
    @Test
    fun orsolyaCastsOneInstantFromTheTopEachTurn() = scenario {
        alice.battlefield(orsolya, "Mountain", "Mountain")
        alice.library(hex, flick)
        bob.battlefield(hare)
        alice.casts(hex, target = "bob", way = permitted(hex))
        resolveAll()
        expect { bob.life shouldBe 18 }
        assertTrue(arcanum.engine.Engine.options(state, alice.id).filterIsInstance<arcanum.engine.input.Input.Cast>()
            .none { it.card == card(flick) && it.way.permission != null }, "a second spell from the top this turn")
    }

    // CR 615.1: damage from creatures to Alice and her creatures is prevented; her blocker still deals its damage, and a spell's isn't prevented
    @Test
    fun uncannyDeflectionPreventsOnlyDamageFromCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", squire)
        bob.battlefield(ox, brute, "Mountain")
        bob.hand(flick)
        alice.hand(deflection)
        turn(number = 2, active = bob, priority = alice)
        alice.casts(deflection)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ox, brute)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(squire to ox)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.life shouldBe 20
            squire.zone shouldBe Zone.Battlefield
            ox.view.obj?.damage shouldBe 2
        }
        advanceTo(TurnStep.PostcombatMain)
        bob.casts(flick, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 19 }
    }

    // CR 115.1, 110.2a: at Alice's upkeep, a creature card from Bob's graveyard comes under her control
    @Test
    fun creedOfTenacityTakesACreatureFromAnyGraveyard() = scenario {
        alice.battlefield(creed)
        bob.graveyard(brute)
        aliceUpkeep()
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            brute.controller shouldBe alice.id
        }
    }

    // CR 701.21a: two permanents sacrificed, two of the three creature cards come back
    @Test
    fun graveRidersTriumphReturnsAsManyAsSacrificed() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", locket, charm, squire)
        alice.graveyard(ox, brute, hare)
        alice.hand(triumph)
        alice.casts(triumph)
        allPass()
        alice.answers(Answer.Objects(listOf(card(locket), card(charm))))
        alice.answers(Answer.Objects(listOf(card(ox), card(brute))))
        expect {
            locket.zone shouldBe Zone.Graveyard
            charm.zone shouldBe Zone.Graveyard
            squire.zone shouldBe Zone.Battlefield
            ox.zone shouldBe Zone.Battlefield
            brute.zone shouldBe Zone.Battlefield
            hare.zone shouldBe Zone.Graveyard
        }
    }

    // CR 701.21a: "that many" can't be more than there are: two sacrificed, the only creature card comes back
    @Test
    fun graveRidersTriumphReturnsAllWhenFewer() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", locket, charm)
        alice.graveyard(ox)
        alice.hand(triumph)
        alice.casts(triumph)
        allPass()
        alice.answers(Answer.Objects(listOf(card(locket), card(charm))))
        if (pending is Decision.ChooseObjects) alice.answers(Answer.Objects(listOf(card(ox))))
        expect { ox.zone shouldBe Zone.Battlefield }
    }

    // CR 701.21a: the cards drawn are the permanents sacrificed (two), not anything about the enters event
    @Test
    fun spitefulHexwingDrawsOneForEachSacrificed() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", locket, charm)
        alice.library(ox, brute, hare)
        alice.hand(hexwing)
        alice.casts(hexwing)
        resolveAll()
        alice.answers(Answer.Objects(listOf(card(locket), card(charm))))
        resolveAll()
        expect {
            alice.hand has ox
            alice.hand has brute
            alice.hand lacks hare
        }
    }

    // CR 508.1c: Bob's creature enchanted by Alice's Aura can't attack Alice, but may attack Carol
    @Test
    fun marisolKeepsEnchantedCreaturesFromAttackingHer() = scenario(players = 3) {
        alice.battlefield(marisol, shackle)
        bob.battlefield(ox)
        attach(shackle, ox)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { bob.attacks(ox, defender = "alice") }
        bob.attacks(ox, defender = "carol")
    }

    // CR 608.2h: only Bob controlled a returned creature: two Faeries (Carol had none)
    @Test
    fun pixieNapGalaCountsOpponentsWhoControlledReturnedCreatures() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", squire)
        bob.battlefield(ox, brute)
        carol.battlefield("Forest")
        alice.hand(gala)
        alice.casts(gala)
        resolveAll()
        expect {
            squire.zone shouldBe Zone.Hand
            ox.zone shouldBe Zone.Hand
            brute.zone shouldBe Zone.Hand
        }
        assertEquals(2, tokens("Faerie Token").size)
    }

    // CR 608.2h: Bob and Carol each controlled one: four Faeries
    @Test
    fun pixieNapGalaMakesTwoForEachSuchOpponent() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield(ox)
        carol.battlefield(brute)
        alice.hand(gala)
        alice.casts(gala)
        resolveAll()
        assertEquals(4, tokens("Faerie Token").size)
    }

    // CR 509.3a: blocking, it deals 1 damage to the creature it blocks
    @Test
    fun spitRoastHurlerPingsWhatItBlocks() = scenario {
        alice.battlefield(hurler)
        bob.battlefield(ox)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(ox)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(hurler to ox)
        resolveAll()
        expect { ox.view.obj?.damage shouldBe 1 }
    }

    // CR 509.3d: blocked by two creatures, it triggers for each and deals 1 damage to each blocker
    @Test
    fun spitRoastHurlerPingsEachBlocker() = scenario {
        alice.battlefield(hurler)
        bob.battlefield(hare, squire)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hurler)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(hare to hurler, squire to hurler)
        resolveAll()
        expect {
            hare.zone shouldBe Zone.Graveyard
            squire.view.obj?.damage shouldBe 1
        }
    }

    // CR 603.2: only the first instant or sorcery spell each turn; a creature spell before it doesn't count
    @Test
    fun tidepoolBrewerGrowsOnlyForTheFirstInstantOrSorcery() = scenario {
        alice.battlefield(brewer, "Forest", "Mountain", "Mountain")
        alice.hand(hare, hex, flick)
        alice.casts(hare)
        resolveAll()
        expect { brewer.power shouldBe 1 }
        alice.casts(hex, target = "bob")
        resolveAll()
        expect { brewer.power shouldBe 3 }
        alice.casts(flick, target = "bob")
        resolveAll()
        expect { brewer.power shouldBe 3 }
    }

    // CR 105.2: a red spell gives a counter, a green one doesn't
    @Test
    fun rovingDruidCountsWhiteBlueBlackOrRedSpells() = scenario {
        alice.battlefield(druid, "Forest", "Mountain")
        alice.hand(hare, hex)
        alice.casts(hare)
        resolveAll()
        expect { druid.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
        alice.casts(hex, target = "bob")
        resolveAll()
        expect { druid.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 115.7, 603.4: a single-target spell's 3 damage to the creature it targets: 3 to each opponent
    @Test
    fun bertrandCopiesSingleTargetSpellDamageToEachOpponent() = scenario(players = 3) {
        alice.battlefield(bertrand, "Mountain")
        alice.hand(jolt)
        bob.battlefield(ox)
        alice.casts(jolt, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            bob.life shouldBe 17
            carol.life shouldBe 17
        }
    }

    // CR 115.7: damage to a player isn't damage to a targeted creature: nothing more
    @Test
    fun bertrandIgnoresDamageToAPlayer() = scenario(players = 3) {
        alice.battlefield(bertrand, "Mountain")
        alice.hand(hex)
        alice.casts(hex, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 20
        }
    }

    // CR 603.2h: Ondine's own enters ability taps Bob's creature: a card; a second tap that turn draws nothing
    @Test
    fun ondineDrawsOnlyOnceEachTurn() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Island", "Island", "Island")
        alice.hand(ondine, gust)
        alice.library(locket, charm)
        bob.battlefield(ox)
        carol.battlefield(brute, hare)
        alice.casts(ondine)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(ox))))
        resolveAll()
        expect {
            ox.tapped shouldBe true
            alice.hand has locket
        }
        alice.casts(gust, targets = listOf(brute, hare))
        resolveAll()
        expect {
            brute.tapped shouldBe true
            alice.hand lacks charm
        }
    }

    // CR 603.2c: tapping two creatures at once is one batch: one card
    @Test
    fun ondineDrawsOnceForTwoTappedTogether() = scenario(players = 3) {
        alice.battlefield(ondine, "Island")
        alice.hand(gust)
        alice.library(locket, charm)
        bob.battlefield(ox)
        carol.battlefield(brute)
        alice.casts(gust, targets = listOf(ox, brute))
        resolveAll()
        expect {
            alice.hand has locket
            alice.hand lacks charm
        }
    }
}
