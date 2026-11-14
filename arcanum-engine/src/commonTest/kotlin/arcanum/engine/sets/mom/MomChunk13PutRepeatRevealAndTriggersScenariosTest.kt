package arcanum.engine.sets.mom

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.eoe.pay
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM chunk 13: three looked-at cards to three places, a process repeated once, a creature returned at end of combat, a reveal adding mana of
 * its colours, a backup ability copied, a cheaper spell cast free or a token, damage to opponents and their battles, a spell's damage bringing a
 * card back from the graveyard, and combat damage triggers once per player or battle. */
class MomChunk13PutRepeatRevealAndTriggersScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val clarity = Cards.invent("Instant of Clarity", "{1}{U}", "Instant",
        "Look at the top three cards of your library. Put one of those cards into your hand, one into your graveyard, and one on the bottom of your library.")
    private val velka = Cards.invent("Velka and Orrin", "{B}{G}{U}", "Legendary Creature — Human Dryad", """
        Whenever you draw your second card each turn, target opponent loses 2 life and you gain 2 life.
        {T}, Sacrifice another creature: Draw a card. You may put a land card from your hand onto the battlefield tapped. If you control eight or more lands, repeat this process once.
    """.trimIndent(), "3/4")
    private val hound = Cards.invent("Orrin's Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val ringtail = Cards.invent("Brawling Ringtail", "{3}{R}", "Creature — Raccoon Warrior",
        "Whenever this creature attacks, up to one target attacking creature gets +2/+0 and gains trample until end of turn. Return that creature to its owner's hand at end of combat. (Return it only if it's on the battlefield.)",
        "3/4")
    private val ysmar = Cards.invent("Ysmar, Hearth of Hues", "{W}{U}{B/P}{R}{G}", "Legendary Creature — Phyrexian Elemental", """
        If you would lose unspent mana, that mana becomes black instead.
        At the beginning of your first main phase, look at the top card of your library. You may reveal that card if it has three or more colored mana symbols in its mana cost. If you do, add three mana in any combination of its colors and put it into your hand. If you don't reveal it, put it into your hand.
    """.trimIndent(), "4/4")
    private val triadHerald = Cards.invent("Triad Herald", "{1}{R}{R/G}{G}", "Creature — Human Shaman", "", "3/3")
    private val duoHerald = Cards.invent("Duo Herald", "{3}{R}{G}", "Creature — Human Shaman", "", "4/4")
    private val phalanx = Cards.invent("Glassguard Phalanx", "{R}{W}", "Creature — Human Soldier", """
        Vigilance
        Whenever a creature you control becomes the target of a backup ability, copy that ability. You may choose new targets for the copy. This ability triggers only once each turn.
    """.trimIndent(), "2/2")
    private val recruit = Cards.invent("Bannerward Recruit", "{2}{W}", "Creature — Human Soldier", """
        Backup 1 (When this creature enters, put a +1/+1 counter on target creature. If that's another creature, it gains the following ability until end of turn.)
        Flying
    """.trimIndent(), "2/2")
    private val torvin = Cards.invent("Torvin and Ashka Rel", "{1}{U}{R}", "Legendary Creature — Human", """
        First strike, menace
        Whenever you cast your first instant or sorcery spell each turn, you may cast a spell with lesser mana value that shares a card type with it from your hand without paying its mana cost. If you don't, create Deckhand Mirko, a legendary 2/1 red Monkey Pirate creature token. It gains haste until end of turn.
    """.trimIndent(), "2/4")
    private val jolt = Cards.invent("Sparkcall Jolt", "{1}{R}", "Instant", "Sparkcall Jolt deals 2 damage to any target.")
    private val insight = Cards.invent("Quickwit Insight", "{U}", "Instant", "Draw a card.")
    private val squallshaper = Cards.invent("Gleeful Squallshaper", "{3}{U}{R}", "Creature — Human Shaman", """
        When this creature enters, create two 1/1 blue and red Elemental creature tokens.
        Whenever you cast a spell that has convoke, this creature deals 1 damage to each opponent and each battle they protect.
    """.trimIndent(), "2/3")
    private val chorus = Cards.invent("Gathered Chorus", "{1}{U}", "Instant", """
        Convoke
        Draw a card.
    """.trimIndent())
    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Gilded Spire", "{2}{R}", "Battle — Siege", "", defense = "5"),
        OracleCard("Gilded Spire Keeper", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val phoenix = Cards.invent("Cinderplume Phoenix", "{1}{R}", "Creature — Phoenix", """
        Flying
        This creature can't block.
        Whenever an instant or sorcery spell you control deals damage to an opponent or battle, you may pay {R}. If you do, return this card from your graveyard to the battlefield. It gains haste until end of turn.
    """.trimIndent(), "2/2")
    private val grosh = Cards.invent("Grosh and Valkiran", "{2}{U}{R}{W}", "Legendary Creature — Orc Dragon", """
        Flying, haste
        Grosh and Valkiran has hexproof as long as it entered this turn.
        Whenever one or more Dragons you control deal combat damage to a player or battle, look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. You may return one of those Dragons to its owner's hand.
    """.trimIndent(), "4/4")
    private val wyrmling = Cards.invent("Cindercoil Wyrmling", "{1}{R}", "Creature — Dragon", "Flying", "2/2")

    private fun Scenario.libraries() = players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") }

    // CR 608.2c: in the order written, one card each to the hand, the graveyard and the bottom of the library (CR 401.4)
    @Test
    fun clarityPutsOneCardInEachPlace() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(clarity)
        alice.library("Plains", "Swamp", "Forest", "Mountain")
        alice.casts(clarity)
        resolveAll()
        alice.chooses("Swamp")
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.hand has "Swamp"
            alice.graveyard has "Forest"
            alice.library.names shouldBe listOf("Mountain", "Plains")
        }
    }

    // CR 608.2c: with eight lands after the first land, the draw and the land happen once more
    @Test
    fun velkaRepeatsTheProcessWithEightLands() = scenario {
        alice.battlefield(velka, hound, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Island")
        alice.library("Swamp", "Plains", "Mountain")
        alice.activates(velka, choosing = listOf(hound))
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        alice.chooses("Swamp")
        resolveAll()
        expect {
            alice.graveyard has hound
            alice.hand.names shouldBe listOf("Plains")
            alice.battlefield has "Island"
            alice.battlefield has "Swamp"
            alice.library.names shouldBe listOf("Mountain")
        }
    }

    // CR 608.2c: with seven lands after the first time, it isn't repeated
    @Test
    fun velkaDoesntRepeatWithFewerLands() = scenario {
        alice.battlefield(velka, hound, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Island")
        alice.library("Swamp", "Plains")
        alice.activates(velka, choosing = listOf(hound))
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf("Swamp")
            alice.library.names shouldBe listOf("Plains")
        }
    }

    // CR 603.7: the targeted attacker gets +2/+0 and trample, and returns to its owner's hand at end of combat
    @Test
    fun ringtailReturnsTheBoostedAttackerAtEndOfCombat() = scenario {
        alice.battlefield(ringtail, hound)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ringtail, hound)
        resolveAll()
        targets(hound)
        resolveAll()
        expect {
            hound.power shouldBe 4
            hound hasKeyword Keyword.Trample.kind
        }
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            alice.hand has hound
            alice.battlefield has ringtail
            bob.life shouldBe 13
        }
    }

    // CR 107.4, 202.1: a card with three coloured symbols (a hybrid one counted once) may be revealed; CR 106.1: three mana of its colours in any
    // combination, and the card goes to the hand
    @Test
    fun ysmarRevealsAThreeSymbolCardForMana() = scenario {
        alice.battlefield(ysmar)
        alice.library(triadHerald, "Island", "Island")
        bob.library("Island", "Island")
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        val among = decision<Decision.ChooseColor>().among
        assertEquals(setOf(Color.Red, Color.Green), among.toSet())
        alice.answers(Answer.ColorChoice(Color.Red))
        alice.answers(Answer.ColorChoice(Color.Green))
        alice.answers(Answer.ColorChoice(Color.Green))
        resolveAll()
        expect {
            alice.hand has triadHerald
            alice.pool.map { it.color } shouldBe listOf(Color.Red, Color.Green, Color.Green)
        }
    }

    // CR 107.4: two coloured symbols aren't enough: no reveal is offered, the card still goes to the hand, no mana
    @Test
    fun ysmarPutsATwoSymbolCardIntoTheHandWithoutMana() = scenario {
        alice.battlefield(ysmar)
        alice.library(duoHerald, "Island", "Island")
        bob.library("Island", "Island")
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        expect {
            alice.hand has duoHerald
            alice.pool.size shouldBe 0
        }
    }

    // CR 702.165a, 707.10: the backup ability targeting a creature you control is copied, and the copy targets this creature instead
    @Test
    fun phalanxCopiesTheBackupAbility() = scenario {
        alice.battlefield(phalanx, hound, "Plains", "Plains", "Plains")
        alice.hand(recruit)
        alice.casts(recruit)
        resolveAll()
        targets(hound)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        targets(phalanx)
        resolveAll()
        expect {
            hound.counters(p1p1) shouldBe 1
            hound hasKeyword Keyword.Flying.kind
            phalanx.counters(p1p1) shouldBe 1
            phalanx hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.2, 608.2g: the first instant cast this turn lets a cheaper instant from the hand be cast free
    @Test
    fun torvinCastsACheaperInstantFree() = scenario {
        alice.battlefield(torvin, "Mountain", "Mountain")
        alice.hand(jolt, insight)
        alice.library("Island", "Island")
        alice.casts(jolt, target = "bob")
        resolveAll()
        alice.chooses(insight)
        resolveAll()
        expect {
            alice.graveyard has insight
            alice.graveyard has jolt
            alice.hand.names shouldBe listOf("Island")
            bob.life shouldBe 18
            alice.battlefield lacks "Deckhand Mirko"
        }
    }

    // CR 608.2c: no spell cast this way, so the legendary Monkey Pirate is created with haste; CR 603.2: a second instant doesn't trigger
    @Test
    fun torvinMakesTheTokenWithoutACheaperSpell() = scenario {
        alice.battlefield(torvin, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(jolt, "Sparkcall Jolt")
        alice.casts(jolt, target = "bob")
        resolveAll()
        expect {
            alice.battlefield has "Deckhand Mirko"
            "Deckhand Mirko" hasKeyword Keyword.Haste.kind
            "Deckhand Mirko".power shouldBe 2
        }
        alice.casts(jolt, target = "bob")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Deckhand Mirko" } shouldBe 1
            bob.life shouldBe 16
        }
    }

    // CR 603.2: the first instant or sorcery counts only those spells: a creature spell cast before it doesn't use up the trigger
    @Test
    fun torvinTriggersOnTheFirstInstantAfterACreatureSpell() = scenario {
        alice.battlefield(torvin, "Forest", "Forest", "Mountain", "Mountain")
        alice.hand(hound, jolt)
        alice.casts(hound)
        resolveAll()
        alice.casts(jolt, target = "bob")
        resolveAll()
        expect {
            alice.battlefield has hound
            alice.battlefield has "Deckhand Mirko"
            bob.life shouldBe 18
        }
    }

    // CR 702.51, 310.8: a convoke spell triggers 1 damage to each opponent and each battle an opponent protects, not to its controller
    @Test
    fun squallshaperHitsEachOpponentAndTheirBattle() = scenario(players = 3) {
        alice.battlefield(squallshaper, "Island", "Island")
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.hand(chorus)
        libraries()
        passPriority()
        decision<Decision.ChoosePlayer>()
        answer(Answer.Player(bob.id))
        advanceTo(TurnStep.BeginningOfCombat)
        alice.casts(chorus)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
            siege.counters(CounterKind.Defense) shouldBe 4
        }
    }

    // CR 120.1, 603.6: an instant's damage to an opponent brings the card back from the graveyard for {R}, with haste
    @Test
    fun phoenixReturnsWhenASpellDamagesAnOpponent() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(phoenix)
        alice.hand(jolt)
        alice.casts(jolt, target = "bob")
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        pay()
        expect {
            alice.battlefield has phoenix
            phoenix hasKeyword Keyword.Haste.kind
        }
    }

    /** Answers the Dragon triggers' choices (keeping the first looked-at card, returning no Dragon) and counts the triggers, each offering only
     * the Dragon of its own batch. */
    private fun Scenario.groshTriggers(): Int {
        var triggers = 0
        var guard = 20
        while (pending != null && guard-- > 0) {
            when (val open = pending) {
                is Decision.ChooseObjects -> {
                    val dragons = open.options.filter { it == card(grosh) || it == card(wyrmling) }
                    if (dragons.isNotEmpty()) {
                        triggers++
                        assertEquals(1, dragons.size)
                        answer(Answer.Objects(emptyList()))
                    } else answer(Answer.Objects(open.options.take(open.min)))
                }
                else -> answer(Answer.Order((open as? Decision.OrderTriggers)?.triggers?.indices?.toList() ?: break))
            }
            resolveAll()
        }
        return triggers
    }

    // CR 603.2c: Dragons dealing combat damage to a player and to a battle trigger it once for each; "those Dragons" are each batch's own
    @Test
    fun groshTriggersOncePerPlayerOrBattle() = scenario {
        alice.battlefield(grosh, wyrmling)
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(grosh), TargetRef.Player(bob.id)), Attack(card(wyrmling), TargetRef.Object(card(siege))))))
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertEquals(2, groshTriggers())
        expect {
            bob.life shouldBe 16
            siege.counters(CounterKind.Defense) shouldBe 3
            alice.hand.size shouldBe 2
        }
    }

    // CR 603.2c, 310.6: two battles dealt combat damage are two triggers, one for each battle
    @Test
    fun groshTriggersOncePerBattle() = scenario(players = 3) {
        alice.battlefield(grosh, wyrmling)
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        libraries()
        val bobsSiege = card(siege, index = 0)
        val carolsSiege = card(siege, index = 1)
        passPriority()
        repeat(2) {
            val protector = decision<Decision.ChoosePlayer>()
            answer(Answer.Player(if (protector.source == bobsSiege) bob.id else carol.id))
        }
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(grosh), TargetRef.Object(bobsSiege)), Attack(card(wyrmling), TargetRef.Object(carolsSiege)))))
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertEquals(2, groshTriggers())
        expect { alice.hand.size shouldBe 2 }
    }
}
