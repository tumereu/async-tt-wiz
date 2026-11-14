package arcanum.engine.sets.one

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.spm.playOut
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 09 in play: cost reductions for Equipment abilities that target this creature and per oil counter
 * (CR 601.2f), mana equal to a sacrificed artifact's mana value (CR 106.1), a permanent that becomes a creature for good (CR 611.2a), copies
 * of the artifacts a spell destroyed (CR 707.2), attaching to the creature that entered, casting a copy of a card exiled at random
 * (CR 707.12), X/1 tokens, a one-use prevention shield that makes tokens (CR 615.5, 615.7), Equipment destroyed by choice, a reflexive
 * trigger after a restricted discard (CR 603.12), a dies trigger counting counters as the creature last existed (CR 603.10a) and "each other
 * player" (3 players).
 */
class OneChunk09EffectsScenariosTest {
    private val oil = CounterKind.Named("oil")

    private val aspirant = Cards.invent("Edgefused Aspirant", "{2}{R}", "Creature — Phyrexian Warrior", """
        Menace
        Equipment spells you cast cost {1} less to cast.
        Activated abilities of Equipment you control that target this creature cost {1} less to activate.
    """, "2/3")
    private val scrapjaw = Cards.invent("Scrapjaw, Iron Tinker", "{2}{R}", "Legendary Creature — Phyrexian Goblin Artificer",
        "{T}, Sacrifice an artifact: Add an amount of {R} equal to the sacrificed artifact's mana value. Spend this mana only to cast artifact spells or activate abilities of artifacts.",
        "3/3")
    private val pillar = Cards.invent("Pillar of Completion", "{2}", "Artifact", """
        {3}, {T}: Search your library for a basic, Sphere, or Locus land card, reveal it, put it into your hand, then shuffle.
        {3}: This artifact becomes a 9/9 Phyrexian Construct artifact creature, loses all abilities, and gains indestructible and toxic 9. Activate only if there are nine or more lands with different names among the basic, Sphere, and Locus lands you control.
    """)
    private val dusk = Cards.invent("Crimson Star's Dusk", "{X}{R}{R}", "Sorcery",
        "Destroy up to X target artifacts. If X is 5 or more, for each artifact destroyed this way, create a token that's a copy of it. Those tokens gain haste. Exile them at the beginning of the next end step.")
    private val pashet = Cards.invent("Pashet, Steadfast Pride", "{1}{W}", "Legendary Creature — Cat Cleric", """
        Whenever Pashet or another Cat you control enters, attach up to one target Equipment you control to that creature.
        Equipped creatures you control get +1/+1.
        {3}{W}{W}: Create a 2/2 white Cat creature token.
    """, "2/2")
    private val pyrefiend = Cards.invent("Fickle Pyrefiend", "{3}{R}{R}{R}", "Creature — Phyrexian Dragon", """
        This spell costs {3} less to cast if you have nine or more cards in your graveyard.
        Flying
        When this creature enters, exile three cards at random from your graveyard. Choose a noncreature, nonland card from among them and copy it. You may cast the copy without paying its mana cost.
    """, "4/4")
    private val thoughtgraft = Cards.invent("Thoughtgraft Engine", "{3}{U}", "Artifact", """
        Flash
        At the beginning of your upkeep, put an oil counter on this artifact.
        Instant and sorcery spells you cast cost {1} less to cast for each oil counter on this artifact.
    """)
    private val furnace = Cards.invent("Furnace of the Red Praetor", "{2}{R}", "Artifact",
        "At the beginning of combat on your turn, put an oil counter on this artifact, then create an X/1 red Phyrexian Horror creature token with trample and haste, where X is the number of oil counters on this artifact. Sacrifice that token at the beginning of the next end step.")
    private val paleDusk = Cards.invent("Pale Star's Dusk", "{X}{W}{W}", "Sorcery",
        "You gain X life. Create X 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and \"This token can't block.\" If X is 5 or more, destroy all other creatures. (Players dealt combat damage by a creature with toxic 1 also get a poison counter.)")
    private val seren = Cards.invent("Seren Vale, Bane of Highfort", "{2}{W}{B}", "Legendary Creature — Phyrexian Knight", """
        Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)
        At the beginning of combat on your turn, the next time target creature would deal combat damage to one or more players this combat, prevent that damage. If damage is prevented this way, create that many 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and "This token can't block."
    """, "3/4")
    private val rouse = Cards.invent("Rouse the Dormant", "{3}{R}", "Sorcery",
        "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. If it's equipped, you may destroy all Equipment attached to that creature.")
    private val manticore = Cards.invent("Ichorheart Manticore", "{2}{U}{R}", "Creature — Phyrexian Chimera", """
        Flying
        Whenever you cast a noncreature spell, put an oil counter on this creature.
        Remove three oil counters from this creature: Draw a card. Then you may discard a nonland card. When you discard a card this way, this creature deals 3 damage to target creature or planeswalker. Activate only as a sorcery.
    """, "2/4")
    private val blisterjelly = Cards.invent("Vigilant Blisterjelly", "{4}{U}{U}", "Creature — Phyrexian Jellyfish", """
        Flying
        This creature enters with an oil counter on it.
        When this creature dies, draw cards equal to the number of oil counters on it.
    """, "4/4")
    private val morvek = Cards.invent("Morvek, Thane of Bargains", "{1}{B}{B}", "Legendary Creature — Phyrexian Zombie", """
        Other creatures you control get -1/-1.
        {1}{B}{B}, {T}: Return target creature card from your graveyard to the battlefield. It gains "If this creature would leave the battlefield, exile it instead of putting it anywhere else." Activate only as a sorcery.
    """, "3/4")
    private val annalist = Cards.invent("Apex Annalist", "{2}", "Artifact Creature — Phyrexian Construct",
        "Whenever a player casts their first multicolored spell each turn, each other player draws a card.", "3/1")

    private val trialEdge = Cards.invent("Trial Edge", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        Equip {2}
    """)
    private val brassIdol = Cards.invent("Brass Reliquary", "{3}", "Artifact", "")
    private val brassSentry = Cards.invent("Brass Sentry", "{2}", "Artifact Creature — Construct", "", "2/2")
    private val unbreakableIdol = Cards.invent("Unbreakable Idol", "{2}", "Artifact", "Indestructible")
    private val sphereA = Cards.invent("Glimmering Sphere Vault", null, "Land — Sphere", "{T}: Add {C}.")
    private val sphereB = Cards.invent("Hollow Sphere Vault", null, "Land — Sphere", "{T}: Add {C}.")
    private val locusA = Cards.invent("Spire Locus Vault", null, "Land — Locus", "{T}: Add {C}.")
    private val locusB = Cards.invent("Deep Locus Vault", null, "Land — Locus", "{T}: Add {C}.")
    private val prideCub = Cards.invent("Pride Cub", "{W}", "Creature — Cat", "", "1/1")
    private val glimpse = Cards.invent("Glimpse Study", "{1}{U}", "Sorcery", "Draw two cards.")
    private val boar = Cards.invent("Ashen Boar", "{2}{G}", "Creature — Boar", "", "3/3")
    private val insight = Cards.invent("Swift Glimmer", "{2}{U}", "Instant", "Draw a card.")
    private val brute = Cards.invent("Ironclad Brute", "{3}{G}", "Creature — Beast", "", "4/4")
    private val lance = Cards.invent("Molten Lance", "{R}", "Instant", "Molten Lance deals 4 damage to target creature.")
    private val twinInsight = Cards.invent("Twin Insight", "{W}{U}", "Sorcery", "You gain 1 life.")

    // CR 601.2f, 602.2b: equipping the Aspirant costs {1} (one land pays); equipping another creature still costs {2}
    @Test
    fun aspirantReducesOnlyAbilitiesTargetingItself() = scenario {
        alice.battlefield("Mountain", aspirant, trialEdge, boar)
        alice.activates(trialEdge, target = aspirant)
        resolveAll()
        expect { aspirant.power shouldBe 3 }
        alice.activates(trialEdge, target = boar)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 106.1, 608.2h: three {R} for a sacrificed artifact with mana value 3
    @Test
    fun scrapjawAddsRedEqualToTheSacrificedManaValue() = scenario {
        alice.battlefield(scrapjaw, brassIdol)
        alice.activates(scrapjaw)
        expect {
            alice.graveyard has brassIdol
            alice.pool.size shouldBe 3
            assertTrue(alice.pool.all { it.color == Color.Red })
        }
    }

    // CR 201.2, 602.5b: eight names (a second Plains doesn't count) aren't enough
    @Test
    fun pillarNeedsNineDifferentNames() = scenario {
        alice.battlefield(pillar, "Plains", "Plains", "Island", "Swamp", "Mountain", "Forest", sphereA, sphereB, locusA)
        assertFailsWith<IllegalArgumentException> { alice.activates(pillar, ability = 1, paying = listOf("Plains", "Plains#1", "Island")) }
    }

    // CR 611.2a: with nine names it becomes a 9/9 Construct for good (still one next turn); CR 613.1f: it lost its search ability but has
    // indestructible and toxic 9
    @Test
    fun pillarBecomesANineNineForGood() = scenario {
        alice.battlefield(pillar, "Plains", "Island", "Swamp", "Mountain", "Forest", sphereA, sphereB, locusA, locusB)
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        alice.activates(pillar, ability = 1, paying = listOf("Plains", "Island", "Swamp"))
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            pillar.power shouldBe 9
            pillar.toughness shouldBe 9
            assertTrue(CardType.Creature in pillar.types && CardType.Artifact in pillar.types)
            assertTrue(Subtype("Construct") in pillar.subtypes)
            pillar hasKeyword Keyword.Indestructible.kind
            pillar hasKeyword Keyword.Toxic.Kind
        }
        // it has no activated abilities left
        assertFails { alice.activates(pillar, ability = 1) }
    }

    // CR 701.8a, 707.2: with X = 5 the destroyed Sentry is copied (the indestructible Idol wasn't destroyed, so no copy); CR 603.7: the copy
    // is exiled at the next end step
    @Test
    fun duskCopiesOnlyWhatItDestroyed() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dusk)
        bob.battlefield(brassSentry, unbreakableIdol)
        alice.casts(dusk, targets = listOf(brassSentry, unbreakableIdol), x = 5)
        resolveAll()
        expect {
            bob.graveyard has brassSentry
            bob.battlefield has unbreakableIdol
            alice.battlefield has brassSentry
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks brassSentry }
    }

    // CR 608.2c: with X = 4 the artifact is destroyed and nothing is copied
    @Test
    fun duskBelowFiveOnlyDestroys() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dusk)
        bob.battlefield(brassSentry)
        alice.casts(dusk, target = brassSentry, x = 4)
        resolveAll()
        expect {
            bob.graveyard has brassSentry
            alice.battlefield lacks brassSentry
        }
    }

    // CR 701.3a: the Equipment moves onto the Cat that entered; it then gets +1/+0 and Pashet's +1/+1
    @Test
    fun pashetAttachesToTheEnteringCat() = scenario {
        alice.battlefield("Plains", pashet, trialEdge)
        alice.hand(prideCub)
        alice.casts(prideCub)
        resolveAll()
        playOut(trialEdge)
        expect {
            prideCub.power shouldBe 3
            prideCub.toughness shouldBe 2
        }
    }

    // CR 601.2f: {3} less with nine cards in the graveyard; CR 707.12: the copy of the one noncreature, nonland card exiled is cast free
    @Test
    fun pyrefiendCastsACopyOfAnExiledSorcery() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(pyrefiend)
        alice.graveyard(glimpse, boar, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.library("Island", "Island")
        alice.casts(pyrefiend)
        random(Answer.RandomObjects(listOf(card(glimpse), card(boar), card("Forest", index = 0))))
        playOut(glimpse)
        expect {
            alice.battlefield has pyrefiend
            alice.exile has glimpse
            alice.exile has boar
            alice.hand.size shouldBe 2
        }
    }

    // CR 601.2f: two oil counters make a {2}{U} instant cost {U}
    @Test
    fun thoughtgraftReducesPerOilCounter() = scenario {
        alice.battlefield("Island")
        alice.battlefield(thoughtgraft, counters = listOf(oil to 2))
        alice.hand(insight)
        alice.library("Island")
        alice.casts(insight)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 608.2h: X counted as the token is created (two oil counters, so 2/1); CR 603.7: sacrificed at the next end step
    @Test
    fun furnaceMakesAnXOneHorror() = scenario {
        alice.battlefield(furnace, counters = listOf(oil to 1))
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            "Phyrexian Horror Token".power shouldBe 2
            "Phyrexian Horror Token".toughness shouldBe 1
            "Phyrexian Horror Token" hasKeyword Keyword.Trample.kind
            "Phyrexian Horror Token" hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks "Phyrexian Horror Token" }
    }

    // CR 107.3: X = 5 gains 5 life and makes five Mites; "all other creatures": every creature but the Mites is destroyed
    @Test
    fun paleDuskKeepsOnlyTheMites() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", boar)
        alice.hand(paleDusk)
        bob.battlefield(brute)
        alice.casts(paleDusk, x = 5)
        resolveAll()
        expect {
            alice.life shouldBe 25
            alice.battlefield.names.count { it == "Phyrexian Mite Token" } shouldBe 5
            alice.graveyard has boar
            bob.graveyard has brute
        }
    }

    // CR 615.7, 615.5: the Brute's 4 combat damage to Bob is prevented and Alice gets four Mites
    @Test
    fun serenPreventsTheHitAndMakesMites() = scenario {
        alice.battlefield(seren, brute)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        answer(Answer.Targets(listOf(target(brute))))
        resolveAll()
        hits(brute)
        expect {
            bob.life shouldBe 20
            alice.battlefield.names.count { it == "Phyrexian Mite Token" } shouldBe 4
        }
    }

    // CR 608.2c, 301.5: the equipped creature is taken, untapped and given haste; Alice chooses to destroy its Equipment
    @Test
    fun rouseDestroysTheEquipmentByChoice() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(rouse)
        bob.battlefield(brute, trialEdge, tapped = true)
        attach(trialEdge, brute)
        alice.casts(rouse, target = brute)
        playOut(may = true)
        expect {
            brute.controller shouldBe alice.id
            brute.tapped shouldBe false
            brute hasKeyword Keyword.Haste.kind
            bob.graveyard has trialEdge
        }
    }

    // CR 603.12: the reflexive trigger happens because a (nonland) card was discarded
    @Test
    fun manticoreDiscardsANonlandCardToDealDamage() = scenario {
        alice.battlefield(manticore, counters = listOf(oil to 3))
        alice.hand(boar, "Forest")
        alice.library("Island")
        bob.battlefield(brute)
        alice.activates(manticore)
        playOut(boar, brute)
        expect {
            alice.graveyard has boar
            brute.view.obj?.damage shouldBe 3
            manticore.counters(oil) shouldBe 0
        }
    }

    // CR 603.10a: two oil counters as it died, so two cards
    @Test
    fun blisterjellyDrawsForItsLastCounters() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(blisterjelly, counters = listOf(oil to 2))
        alice.hand(lance)
        alice.library("Island", "Island", "Island")
        alice.casts(lance, target = blisterjelly)
        resolveAll()
        expect {
            alice.graveyard has blisterjelly
            alice.hand.size shouldBe 2
        }
    }

    // 3 players: Bob's first multicolored spell makes Alice and Carol draw, not Bob; his second doesn't trigger
    @Test
    fun annalistMakesEachOtherPlayerDraw() = scenario(players = 3) {
        alice.battlefield(annalist)
        alice.library("Island")
        bob.library("Island")
        carol.library("Island")
        bob.battlefield("Plains", "Island", "Plains", "Island")
        bob.hand(twinInsight, twinInsight)
        turn(number = 2, active = bob)
        bob.casts(twinInsight)
        resolveAll()
        bob.casts(twinInsight)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            carol.hand.size shouldBe 1
            bob.hand.size shouldBe 0
            bob.life shouldBe 22
        }
    }

    // CR 614.1a: the returned Boar is a 2/2 under the -1/-1, and when it would die it's exiled instead
    @Test
    fun morvekReturnsACreatureThatIsExiledWhenItLeaves() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", morvek)
        alice.graveyard(boar)
        alice.hand(lance)
        alice.activates(morvek, target = boar, paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        expect { boar.power shouldBe 2; boar.toughness shouldBe 2 }
        alice.casts(lance, target = boar)
        resolveAll()
        expect {
            alice.exile has boar
            alice.graveyard lacks boar
        }
    }
}
