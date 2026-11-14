package arcanum.engine.sets.dmr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Dominaria Remastered chunk 05 in play: a discard replaced by entering with counters (CR 614.1a, 122.6), reveal until a creature
 * (CR 701.20), a life gain replaced by none (CR 119.10), a per-creature "as long as it's not attacking" (CR 611.3a), "enchanted" (CR 303.4),
 * tokens for each attachment (CR 111.1), every player's spells cheaper (CR 601.2f), the next instant or sorcery uncounterable (CR 611.2f),
 * a spell castable only during combat with a delayed sacrifice (CR 601.3, 603.7), a chosen-type cost reduction (CR 607.2d), divided
 * prevention shields (CR 601.2d, 615.7), counters moved onto any number of creatures (CR 122.5) and "each other spell cast this turn".
 */
class DmrChunk05ConditionsAndRestrictionsScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val duodecipede = Cards.invent("Duodecipede", "{4}", "Artifact Creature — Golem",
        "If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield with two +1/+1 counters on it " +
            "instead of putting it into your graveyard.", "3/3")
    private val warrenkeeper = Cards.invent("Warrenkeeper", "{3}{G}", "Creature — Elf",
        "When this creature dies, you may exile it. If you do, reveal cards from the top of your library until you reveal a creature card. " +
            "Put that card onto the battlefield and put all other cards revealed this way into your graveyard.", "2/2")
    private val maelstrom = Cards.invent("Brimstone Maelstrom", "{1}{R}{R}", "Enchantment", """
        At the beginning of each player's upkeep, this enchantment deals 2 damage to that player.
        If a player would gain life, that player gains no life instead.
    """)
    private val elder = Cards.invent("Vesperan Elder", "{2}{G}{G}{W}{W}{U}{U}", "Legendary Creature — Elder Dragon", """
        Flying
        At the beginning of your upkeep, sacrifice Vesperan Elder unless you pay {G}{W}{U}.
        Each untapped creature you control gets +0/+2 as long as it's not attacking.
        {W}: Vesperan Elder gets +0/+1 until end of turn.
    """, "7/7")
    private val sentinel = Cards.invent("Thranic Sentinel", "{5}", "Artifact Creature — Golem",
        "As long as this creature is enchanted, it gets +2/+2 and has flying, first strike, and trample.", "3/3")
    private val orvek = Cards.invent("Orvek, Warden of the Pyre", "{2}{R}", "Legendary Creature — Human Shaman",
        "At the beginning of combat on your turn, for each Aura and Equipment attached to Orvek, create a 3/1 red Elemental creature token " +
            "with trample and haste. Exile those tokens at the beginning of the next end step.", "3/2")
    private val circlet = Cards.invent("Circlet of Rousing", "{2}", "Artifact", "Spells cost {1} less to cast.")
    private val overmastery = Cards.invent("Overmastery", "{R}", "Sorcery", """
        The next instant or sorcery spell you cast this turn can't be countered.
        Draw a card.
    """)
    private val clasp = Cards.invent("Vertebral Clasp", "{3}{U}{U}{B}", "Instant", """
        Cast this spell only during combat.
        Untap target creature you don't control and gain control of it. It gains haste until end of turn. At the beginning of the next end step, sacrifice it. If you do, you gain life equal to its toughness.
    """)
    private val incubator = Cards.invent("Brood Incubator", "{3}", "Artifact", """
        As this artifact enters, choose a creature type.
        Creature spells of the chosen type cost {2} less to cast.
    """)
    private val touch = Cards.invent("Salving Touch", "{1}{W}", "Instant",
        "Prevent the next 5 damage that would be dealt this turn to any number of targets, divided as you choose.")
    private val rememberedElder = Cards.invent("Remembered Elder", "{3}{G}", "Creature — Elemental", """
        Whenever a player casts a spell, you may put a +1/+1 counter on this creature.
        At the beginning of your upkeep, you may move any number of +1/+1 counters from this creature onto other creatures.
    """, "0/3")
    private val entity = Cards.invent("Tempest Entity", "{1}{R}", "Creature — Elemental", """
        Haste
        This creature enters with a +1/+1 counter on it for each other spell cast this turn.
    """, "1/1")

    private val rot = Cards.invent("Cerebral Rot", "{2}{B}", "Sorcery", "Target player discards two cards.")
    private val jolt = Cards.invent("Scorchbolt Jolt", "{R}", "Instant", "Scorchbolt Jolt deals 3 damage to any target.")
    private val lash = Cards.invent("Ember Lash", "{1}{R}", "Sorcery", "Ember Lash deals 2 damage to any target.")
    private val draught = Cards.invent("Mellow Draught", "{1}{W}", "Instant", "Each player gains 4 life.")
    private val quash = Cards.invent("Quash the Notion", "{U}{U}", "Instant", "Counter target spell.")
    private val grazer = Cards.invent("Thornback Grazer", "{1}{G}", "Creature — Beast", "", "2/2")
    private val picket = Cards.invent("Watchful Picket", "{1}{W}", "Creature — Soldier", "Vigilance", "2/2")
    private val blessing = Cards.invent("Minor Blessing", "{W}", "Enchantment — Aura", "Enchant creature")
    private val spur = Cards.invent("Tarnished Spur", "{1}", "Artifact — Equipment", "Equip {1}")

    // CR 614.1a, 122.6: an opponent's spell makes alice discard it: it enters with two +1/+1 counters instead
    @Test
    fun discardedByAnOpponentItEntersWithCounters() = scenario {
        alice.hand(duodecipede, "Island")
        bob.hand(rot)
        bob.castsWithoutCosts(rot, target = "alice")
        resolve()
        expect {
            alice.battlefield has duodecipede
            duodecipede.counters(p1p1) shouldBe 2
            duodecipede.power shouldBe 5
            alice.graveyard.names shouldBe listOf("Island")
        }
    }

    // CR 701.20: exiled as it dies; the first creature card revealed enters, the other revealed cards go to the graveyard
    @Test
    fun diesExiledThenRevealsUntilACreature() = scenario {
        alice.battlefield(warrenkeeper)
        alice.library("Island", "Forest", grazer, "Plains")
        alice.hand(jolt)
        alice.castsWithoutCosts(jolt, target = warrenkeeper)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.exile has warrenkeeper
            alice.battlefield has grazer
            alice.graveyard has "Island"
            alice.graveyard has "Forest"
            alice.library.names shouldBe listOf("Plains")
        }
    }

    /** Declining the exile reveals nothing. */
    @Test
    fun notExiledRevealsNothing() = scenario {
        alice.battlefield(warrenkeeper)
        alice.library("Island", grazer)
        alice.hand(jolt)
        alice.castsWithoutCosts(jolt, target = warrenkeeper)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.graveyard has warrenkeeper
            alice.library.names shouldBe listOf("Island", grazer)
        }
    }

    // CR 119.10, 614.1a: no player gains life, so nothing changes
    @Test
    fun noPlayerGainsLife() = scenario(players = 3) {
        alice.battlefield(maelstrom)
        alice.hand(draught)
        alice.castsWithoutCosts(draught)
        resolve()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 603.2: each player's upkeep: 2 damage to that player
    @Test
    fun damagesThePlayerWhoseUpkeepItIs() = scenario(players = 3) {
        alice.battlefield(maelstrom)
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 611.3a: untapped and not attacking: an attacker with vigilance loses the bonus, a tapped attacker never had it
    @Test
    fun untappedNonattackingCreaturesGetToughness() = scenario {
        alice.battlefield(elder, picket, grazer)
        expect {
            picket.toughness shouldBe 4
            grazer.toughness shouldBe 4
            elder.toughness shouldBe 9
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(picket, grazer)
        expect {
            picket.tapped shouldBe false
            picket.toughness shouldBe 2
            grazer.toughness shouldBe 2
            elder.toughness shouldBe 9
        }
    }

    // CR 303.4: enchanted means an Aura is attached
    @Test
    fun enchantedItGetsTheBonus() = scenario {
        alice.battlefield(sentinel, blessing)
        attach(blessing, sentinel)
        expect {
            sentinel.power shouldBe 5
            sentinel.toughness shouldBe 5
            sentinel hasKeyword Keyword.Flying.kind
            sentinel hasKeyword Keyword.FirstStrike.kind
            sentinel hasKeyword Keyword.Trample.kind
        }
    }

    /** An Equipment doesn't enchant it. */
    @Test
    fun equippedIsNotEnchanted() = scenario {
        alice.battlefield(sentinel, spur)
        attach(spur, sentinel)
        expect {
            sentinel.power shouldBe 3
            sentinel lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 111.1, 603.7: one token for each Aura and Equipment attached, exiled at the next end step
    @Test
    fun tokensForEachAttachmentExiledAtEndStep() = scenario {
        alice.battlefield(orvek, blessing, spur)
        attach(blessing, orvek)
        attach(spur, orvek)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { tokens("Elemental Token").size shouldBe 2 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { tokens("Elemental Token").size shouldBe 0 }
    }

    // CR 601.2f: an opponent's spell costs {1} less too
    @Test
    fun everyPlayersSpellsCostLess() = scenario {
        alice.battlefield(circlet)
        bob.battlefield("Mountain")
        bob.hand(lash)
        turn(number = 2, active = bob)
        bob.casts(lash, target = "alice")
        resolve()
        expect {
            alice.life shouldBe 18
            bob.hand lacks lash
        }
    }

    // CR 611.2f: a creature spell cast first doesn't use it up; the next instant can't be countered
    @Test
    fun nextInstantOrSorceryCantBeCountered() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand(overmastery, grazer, jolt)
        alice.library("Island")
        bob.hand(quash)
        alice.casts(overmastery, paying = listOf("Mountain"))
        resolve()
        alice.casts(grazer, paying = listOf("Forest", "Forest#1"))
        resolve()
        alice.casts(jolt, target = "bob", paying = listOf("Mountain#1"))
        alice.passes()
        bob.castsWithoutCosts(quash, target = jolt)
        resolveAll()
        expect {
            alice.battlefield has grazer
            bob.graveyard has quash
            bob.life shouldBe 17
        }
    }

    // CR 601.3: only during combat
    @Test
    fun castOnlyDuringCombat() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp", "Swamp")
        bob.battlefield(grazer)
        alice.hand(clasp)
        assertFailsWith<IllegalArgumentException> { alice.casts(clasp, target = grazer) }
    }

    // CR 603.7, 608.2h: untapped and stolen with haste; sacrificed at the next end step for life equal to its toughness
    @Test
    fun stealsThenSacrificesForLife() = scenario {
        bob.battlefield(grazer, tapped = true)
        alice.hand(clasp)
        turn(step = TurnStep.BeginningOfCombat)
        alice.castsWithoutCosts(clasp, target = grazer)
        resolve()
        expect {
            grazer.controller shouldBe alice.id
            grazer.tapped shouldBe false
            grazer hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.graveyard has grazer
            alice.life shouldBe 22
        }
    }

    // CR 607.2d, 601.2f: creature spells of the chosen type cost {2} less (generic mana only: {1}{G} costs {G}, paid with the last Forest)
    @Test
    fun chosenTypeCreatureSpellsCostLess() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(incubator, grazer)
        alice.casts(incubator)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Beast")))
        alice.casts(grazer)
        resolve()
        expect { alice.battlefield has grazer }
    }

    // CR 601.2d, 615.7: 3 prevented for the creature, 2 for the player
    @Test
    fun dividedPreventionShields() = scenario {
        alice.battlefield(grazer)
        alice.hand(touch, jolt, lash)
        alice.castsWithoutCosts(touch, targets = listOf(grazer, "alice"), divided = listOf(3, 2))
        resolve()
        alice.castsWithoutCosts(jolt, target = grazer)
        resolve()
        alice.castsWithoutCosts(lash, target = "alice")
        resolve()
        expect {
            alice.battlefield has grazer
            card(grazer).view.obj?.damage shouldBe 0
            alice.life shouldBe 20
        }
    }

    /** The shield of the player is 2: 3 damage leaves 1. */
    @Test
    fun dividedShieldHoldsOnlyItsShare() = scenario {
        alice.battlefield(grazer)
        alice.hand(touch, jolt)
        alice.castsWithoutCosts(touch, targets = listOf(grazer, "alice"), divided = listOf(3, 2))
        resolve()
        alice.castsWithoutCosts(jolt, target = "alice")
        resolve()
        expect { alice.life shouldBe 19 }
    }

    // CR 122.5: moved to any of the other creatures, as many as chosen for each
    @Test
    fun movesCountersOntoOtherCreatures() = scenario {
        alice.battlefield(rememberedElder, counters = listOf(p1p1 to 3))
        alice.battlefield(grazer, picket)
        aliceUpkeep()
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Number(2))
        answer(Answer.Number(1))
        resolveAll()
        expect {
            rememberedElder.counters(p1p1) shouldBe 0
            grazer.counters(p1p1) shouldBe 2
            picket.counters(p1p1) shouldBe 1
        }
    }

    /** Its own spell isn't one of the other spells. */
    @Test
    fun countsEachOtherSpellCastThisTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(jolt, entity)
        alice.casts(jolt, target = "bob")
        resolve()
        alice.casts(entity)
        resolve()
        expect {
            entity.counters(p1p1) shouldBe 1
            entity.power shouldBe 2
        }
    }
}
