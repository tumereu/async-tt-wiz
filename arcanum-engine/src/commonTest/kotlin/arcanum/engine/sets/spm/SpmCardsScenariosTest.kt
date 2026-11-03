package arcanum.engine.sets.spm

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Marvel's Spider-Man cards in play (heroes, Sagas and modes): a card type chosen on entering that taxes spells, damage prevented
 * into counters, counters handed on as a creature leaves, a counter for mana at each first main phase, counters doubled, a copy of
 * the next creature spell that isn't legendary, a chosen card name, a discard or 3 life for each player, forced attacks, modes of
 * one or both, a creature becoming a 1/1 Citizen, a Saga making a legendary Spider Hero. */
class SpmCardsScenariosTest {
    private val thessaly = Cards.invent("Thessaly, Mind Spinner", "{2}{W}", "Legendary Creature — Spider Human Hero", """
        Web-slinging {W} (You may cast this spell for {W} if you also return a tapped creature you control to its owner's hand.)
        As Thessaly enters, look at an opponent's hand, then choose a card type other than creature.
        Spells of the chosen type cost {1} more to cast.
    """, "3/3")
    private val bloom = Cards.invent("Pallid Bloom, Dread Mender", "{W}{W}{W}{W}{W}", "Legendary Creature — Symbiote Hero", """
        When Pallid Bloom enters, if he was cast, return target creature card from your graveyard to the battlefield.
        If damage would be dealt to Pallid Bloom, prevent that damage and put that many +1/+1 counters on him.
    """, "5/5")
    private val captain = Cards.invent("Dutiful Precinct Captain", "{1}{W}", "Creature — Human Detective", """
        This creature enters with a +1/+1 counter on it.
        When this creature leaves the battlefield, put its +1/+1 counters on target creature you control.
    """, "1/1")
    private val onlookers = Cards.invent("Roaring Onlookers", "{1}{R/G}", "Creature — Human Citizen",
        "At the beginning of each player's first main phase, that player may put a +1/+1 counter on this creature. If they do, they add {C} for each counter on it.", "2/2")
    private val webstalker = Cards.invent("Unrivaled Webstalker", "{3}{R}{G}{W}", "Legendary Creature — Spider Human Hero", """
        First strike, haste
        Camouflage — {2}: Put a +1/+1 counter on Unrivaled Webstalker. He gains hexproof and becomes colorless until end of turn.
        Whenever you attack, double the number of each kind of counter on each Spider and legendary creature you control.
    """, "4/3")
    private val lineage = Cards.invent("The Mirrored Lineage", "{3}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Surveil 3.
        II — When you next cast a creature spell this turn, copy it, except the copy isn't legendary.
        III — Choose a card name. Whenever a creature with the chosen name deals combat damage to a player this turn, draw a card.
    """)
    private val tragedy = Cards.invent("The Bridge Fall Tragedy", "{2}{B}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Destroy target creature.
        II — Each player may discard a card. Each player who doesn't loses 3 life.
        III — Exile any number of target players' graveyards.
    """)
    private val bloodbath = Cards.invent("Citywide Bloodbath", "{4}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Until your next turn, each creature attacks each combat if able and attacks a player other than you if able.
        II — Add {R}{R}{R}.
        III — This Saga deals 5 damage to each opponent.
    """)
    private val birth = Cards.invent("Birth of the Masked Climber", "{1}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Create a 2/1 green Spider creature token with reach.
        II — Put a +1/+1 counter on target creature you control. It becomes a legendary Spider Hero in addition to its other types.
        III — Target creature you control gains double strike until end of turn.
    """)
    private val brock = Cards.invent("Brock Hale, Devoted Fan", "{1}{W}", "Legendary Creature — Human Citizen", """
        Flash
        When Brock Hale enters, choose one or both —
        • Heckle — Tap target creature.
        • Hero Worship — Untap target creature.
    """, "2/2")
    private val persona = Cards.invent("Hidden Persona", "{U}", "Instant", """
        Choose one —
        • Conceal — Until end of turn, target creature you control becomes a Citizen with base power and toughness 1/1 and gains hexproof.
        • Reveal — Until end of turn, target creature you control becomes a Hero with base power and toughness 3/4 and gains flying and vigilance.
    """)
    private val mask = Cards.invent("Mask Thrown Away", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature is a Citizen with base power and toughness 1/1. It has defender and loses all other abilities. (It also loses all other creature types.)
    """)
    private val diner = Cards.invent("Vigilante Diner", "{R}", "Sorcery", """
        Choose one —
        • Date Night — Exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.
        • Patrol Night — One or two target creatures each get +1/+0 and gain first strike until end of turn.
    """)
    private val zzip = Cards.invent("Zzip!", "{W}", "Instant", "Target creature gets +2/+2 and gains flying until end of turn. If it's a Spider, you gain 2 life.")
    private val burden = Cards.invent("Burden of Might . . .", "{3}{W}", "Enchantment — Aura", """
        Enchant creature you control
        Enchanted creature gets +2/+2 for each Aura and Equipment attached to it.
        All damage that would be dealt to you is dealt to enchanted creature instead.
    """)
    private val bolt = Cards.invent("Galvanic Lash", "{2}{R}", "Sorcery", """
        Galvanic Lash deals 4 damage to target creature.
        Mayhem {1}{R} (You may cast this card from your graveyard for {1}{R} if you discarded it this turn. Timing rules still apply.)
    """)
    private val jolt = Cards.invent("Static Jolt", "{R}", "Instant", "Static Jolt deals 2 damage to any target.")
    private val mercenaries = Cards.invent("Feral Pack Mercenaries", "{2}{W}", "Creature — Human Mercenary",
        "At the beginning of combat on your turn, up to one target creature gains first strike and vigilance until end of turn.", "2/3")
    private val bear = spmBear
    private val ogre = spmOgre
    private val spider = spmSpider

    /** Entering, it shows the opponent's hand and taxes the chosen card type: an instant costs {1} more for both players. */
    // CR 614.12, 607.2d, 601.2f
    @Test
    fun chosenCardTypeCostsMore() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain", "Mountain")
        alice.hand(thessaly, jolt)
        bob.hand(jolt)
        bob.battlefield("Mountain")
        alice.casts(thessaly, paying = listOf("Plains", "Plains", "Plains"))
        drive({ d: Decision -> d is Decision.ChooseCardType } to { answer(Answer.CardTypeChoice(CardType.Instant)) })
        expect { thessaly.zone shouldBe Zone.Battlefield }
        alice.casts(jolt, target = "bob", paying = listOf("Mountain", "Mountain"))
        resolveAll()
        assertEquals(18, bob.life)
    }

    /** Damage to it is prevented and becomes +1/+1 counters. */
    // CR 615.5
    @Test
    fun damageBecomesCounters() = scenario {
        alice.battlefield(bloom)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        turn(active = bob)
        bob.casts(jolt, target = bloom)
        resolveAll()
        expect {
            bloom.counters(p1p1) shouldBe 2
            bloom.power shouldBe 7
        }
    }

    /** Cast, its enters trigger returns a creature card from the graveyard. */
    // CR 603.4
    @Test
    fun castBloomReturnsACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(bear)
        alice.hand(bloom)
        alice.casts(bloom)
        resolveAll()
        if (pending is Decision.Targets) targets(bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    /** Leaving the battlefield, its +1/+1 counters go onto another creature. */
    // CR 603.10a, 608.2h
    @Test
    fun countersHandedOnAsItLeaves() = scenario {
        alice.battlefield(bear, "Plains", "Plains")
        alice.hand(captain)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        alice.casts(captain)
        resolveAll()
        expect { captain.counters(p1p1) shouldBe 1 }
        alice.passes()
        bob.casts(jolt, target = captain)
        resolveAll()
        if (pending is Decision.Targets) targets(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** At each player's first main phase that player may put a counter on it and add {C} for each counter. */
    // CR 505.1, 106.1
    @Test
    fun countersForColorlessMana() = scenario {
        alice.battlefield(onlookers)
        alice.library(bear, bear)
        bob.library(bear, bear)
        turn(number = 3, active = alice, step = TurnStep.Draw)
        allPass()
        drive({ d: Decision -> d is Decision.May } to { yes() })
        expect { onlookers.counters(p1p1) shouldBe 1 }
        assertEquals(1, alice.pool.size)
    }

    /** "Whenever you attack": counters doubled on each Spider and legendary creature; the camouflage ability makes it colorless. */
    // CR 701.10e, 105.2c
    @Test
    fun countersDoubledAndColorless() = scenario {
        alice.battlefield(webstalker, "Plains", "Plains")
        alice.battlefield(spider, counters = listOf(p1p1 to 2))
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.activates(webstalker)
        resolveAll()
        expect {
            webstalker.colors shouldBe emptyList()
            webstalker hasKeyword Keyword.Hexproof
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(webstalker)
        resolveAll()
        expect {
            webstalker.counters(p1p1) shouldBe 2
            spider.counters(p1p1) shouldBe 4
            bear.counters(p1p1) shouldBe 1
        }
    }

    /** Chapter II: the next creature spell this turn is copied, and the copy (a token) isn't legendary. */
    // CR 707.10, 707.10c, 707.9b
    @Test
    fun nextCreatureSpellCopiedNotLegendary() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(spmHero)
        chapter(lineage, 2)
        resolveAll()
        alice.casts(spmHero)
        resolveAll()
        val heroes = state.battlefield.filter { state.obj(it)?.definition?.faces?.firstOrNull()?.name == spmHero || state.obj(it)?.token == true }
        assertEquals(2, heroes.size)
        val token = heroes.first { state.obj(it)!!.token }
        assertTrue(arcanum.api.Supertype.Legendary !in view()[token]!!.characteristics!!.supertypes)
    }

    /** [saga] with [n] - 1 lore counters at the start of alice's draw step: after it, chapter [n] triggers (CR 714.3b). */
    private fun arcanum.engine.scenario.Scenario.chapter(saga: String, n: Int) {
        alice.battlefield(saga, counters = listOf(arcanum.api.CounterKind.Lore to n - 1))
        alice.library(ogre, ogre, ogre)
        bob.library(ogre, ogre)
        turn(number = 3, step = TurnStep.Draw)
        allPass()
    }

    /** Chapter III: a chosen name; that creature's combat damage to a player draws a card this turn. */
    // CR 603.7b, 201.3
    @Test
    fun chosenNameDraws() = scenario {
        alice.battlefield(bear)
        chapter(lineage, 3)
        drive({ d: Decision -> d is Decision.ChooseCardName } to { answer(Answer.CardName(bear)) })
        val hand = alice.hand.size
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        if (state.priority.holder == null) bob.blocks()
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        assertEquals(hand + 1, alice.hand.size)
    }

    /** Chapter II: a player who doesn't discard loses 3 life. */
    // CR 101.4
    @Test
    fun discardOrLoseLife() = scenario {
        alice.hand(bear)
        chapter(tragedy, 2)
        drive(
            { d: Decision -> d is Decision.May && d.player == alice.id } to { yes(true) },
            { d: Decision -> d is Decision.May } to { yes(false) },
            { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(bear) },
        )
        assertEquals(20, alice.life)
        assertEquals(17, bob.life)
        expect { alice.graveyard has bear }
    }

    /** Chapter III exiles the targeted graveyards. */
    // CR 115.1
    @Test
    fun exileTargetGraveyards() = scenario {
        bob.graveyard(ogre)
        alice.graveyard(bear)
        chapter(tragedy, 3)
        drive({ d: Decision -> d is Decision.Targets } to { targets("bob") })
        expect {
            bob.graveyard.isEmpty()
            alice.graveyard has bear
        }
    }

    /** Chapter I: each creature must attack (a player other than its controller, if able). */
    // CR 508.1d
    @Test
    fun creaturesMustAttack() = scenario {
        alice.battlefield(bear)
        chapter(bloodbath, 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertTrue(runCatching { alice.attacks() }.isFailure, "the bear must attack")
        alice.attacks(bear)
        assertTrue(state.combat!!.attackers.any { a -> a.id == card(bear) })
    }

    /** Chapter II: a counter, and the creature becomes a legendary Spider Hero for good. */
    // CR 205.1b, 205.4
    @Test
    fun becomesLegendarySpiderHero() = scenario {
        alice.battlefield(bear)
        chapter(birth, 2)
        drive({ d: Decision -> d is Decision.Targets } to { targets(bear) })
        expect {
            bear.counters(p1p1) shouldBe 1
            bear.subtypes shouldBe listOf(Subtype("Bear"), Subtype("Spider"), Subtype("Hero"))
        }
        assertTrue(arcanum.api.Supertype.Legendary in view()[card(bear)]!!.characteristics!!.supertypes)
    }

    /** One or both modes: tap one creature and untap another. */
    // CR 700.2
    @Test
    fun oneOrBothModes() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(brock)
        bob.battlefield(ogre)
        alice.casts(brock)
        resolveAll()
        val order = ArrayDeque(listOf(ogre, bear))
        drive(
            { d: Decision -> d is Decision.Modes } to { answer(Answer.Modes(listOf(0, 1))) },
            { d: Decision -> d is Decision.Targets } to { targets(order.removeFirst()) },
        )
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    /** Conceal: a 1/1 Citizen with hexproof until end of turn. */
    // CR 205.1b, 613.4b
    @Test
    fun concealedAsACitizen() = scenario {
        alice.battlefield(ogre, "Island")
        alice.hand(persona)
        alice.casts(persona, target = ogre, modes = listOf(0))
        resolveAll()
        expect {
            ogre.power shouldBe 1
            ogre.subtypes shouldBe listOf(Subtype("Citizen"))
            ogre hasKeyword Keyword.Hexproof
        }
    }

    /** The Aura: a 1/1 Citizen with defender and no other abilities. */
    // CR 613.1d, 613.1f, 613.4b
    @Test
    fun auraMakesADefenderCitizen() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(mask)
        bob.battlefield(webstalker)
        alice.casts(mask, target = webstalker)
        resolveAll()
        expect {
            webstalker.power shouldBe 1
            webstalker.subtypes shouldBe listOf(Subtype("Citizen"))
            webstalker hasKeyword Keyword.Defender
            webstalker lacksKeyword Keyword.FirstStrike
        }
    }

    /** Date Night: one of the two exiled cards may be played until the end of your next turn. */
    // CR 305.1, 601.2a
    @Test
    fun dateNightPlaysAnExiledCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(diner)
        alice.library(jolt, bear)
        alice.casts(diner, modes = listOf(0))
        drive({ d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(jolt) })
        alice.casts(jolt, target = "bob", paying = listOf("Mountain#1"))
        resolveAll()
        assertEquals(18, bob.life)
    }

    /** Patrol Night: one or two creatures get +1/+0 and first strike. */
    // CR 601.2c
    @Test
    fun patrolNightPumpsTwo() = scenario {
        alice.battlefield("Mountain", bear, ogre)
        alice.hand(diner)
        alice.casts(diner, modes = listOf(1), targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.power shouldBe 3
            ogre hasKeyword Keyword.FirstStrike
        }
    }

    /** A Spider gains 2 life; another creature doesn't. */
    // CR 608.2c
    @Test
    fun spiderGainsLife() = scenario {
        alice.battlefield("Plains", "Plains", spider, bear)
        alice.hand(zzip, zzip)
        alice.casts(zzip, target = spider)
        resolveAll()
        assertEquals(22, alice.life)
        alice.casts(zzip, target = bear)
        resolveAll()
        assertEquals(22, alice.life)
        expect { bear hasKeyword Keyword.Flying }
    }

    /** The Aura's creature takes the damage dealt to its controller, and grows with each Aura on it. */
    // CR 614.1a, 613.4c
    @Test
    fun damageToYouGoesToTheCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", giant)
        alice.hand(burden)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        alice.casts(burden, target = giant)
        resolveAll()
        expect { giant.power shouldBe 7 }
        alice.passes()
        bob.casts(jolt, target = "alice")
        resolveAll()
        assertEquals(20, alice.life)
        expect { giant.view.obj!!.damage shouldBe 2 }
    }

    private val giant = spmGiant

    /** Up to one target creature gains first strike and vigilance at the beginning of combat. */
    // CR 507.1
    @Test
    fun combatGivesFirstStrike() = scenario {
        alice.battlefield(mercenaries, bear)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        turn(number = 3, step = TurnStep.PrecombatMain)
        allPass()
        drive({ d: Decision -> d is Decision.Targets } to { targets(bear) })
        expect {
            bear hasKeyword Keyword.FirstStrike
            bear hasKeyword Keyword.Vigilance
        }
    }

    @Suppress("unused") private val unused = listOf(bolt, Color.Red)
}
