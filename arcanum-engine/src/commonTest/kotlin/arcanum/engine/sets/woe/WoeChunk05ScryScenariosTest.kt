package arcanum.engine.sets.woe

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Supertype
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** WOE chunk 05, Scry: the invented names carry the oracle text of the cards that need something the engine tests haven't played. */
class WoeChunk05ScryScenariosTest {
    private val hilvara = Cards.invent("Hilvara of the Frosted Crown", "{2}{W}{U}", "Legendary Creature — Human Warlock", """
        Whenever you tap an untapped creature an opponent controls, you may pay {1}. When you do, choose one —
        • Create a 4/4 white and blue Elemental creature token.
        • Put a +1/+1 counter on each creature you control.
        • Scry 2, then draw a card.
    """.trimIndent(), "3/4")
    private val dive = Cards.invent("Dive into Autumn", "{1}{W}", "Instant", "Tap up to one target creature. Scry 1, then draw a card.")
    private val lurker = Cards.invent("Thornpath Lurker", "{1}{G}", "Creature — Beast", "", "2/2")
    private val sprite = Cards.invent("Lantern Sprite", "{1}{W}", "Creature — Faerie", "", "1/1")
    private val pinnacle = Cards.invent("Unquiet Pinnacle", null, "Land", """
        This land enters tapped.
        {T}: Add {U} or {R}.
        {U}{R}: Until end of turn, this land becomes a 2/1 blue and red Elemental creature with "During your turn, this creature has first strike." It's still a land.
        Whenever this land attacks, scry 1.
    """.trimIndent())
    private val wallguard = Cards.invent("Drowsing Wallguard", "{W}", "Creature — Human Knight", """
        Whenever an enchantment you control enters, scry 1.
        {2}{W}: This creature gets +1/+1 until end of turn for each enchantment you control.
    """.trimIndent(), "1/1")
    private val yarrow = Cards.invent("Yarrow, Greenbark Regent", "{2}{G}{W}", "Legendary Creature — Elf Noble", """
        {2}, {T}: Choose target enchantment you control that doesn't have the same name as another permanent you control. Create a token that's a copy of it, except it isn't legendary. If the token is an Aura, untap Yarrow, then scry 2. Activate only as a sorcery.
    """.trimIndent(), "4/4")
    private val ward = Cards.invent("Quilted Ward", "{1}{W}", "Legendary Enchantment")
    private val rune = Cards.invent("Chalk Rune", "{W}", "Enchantment")
    private val mossAura = Cards.invent("Moss Aura", "{G}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val pepper = Cards.invent("Sir Pepper, the Feast Breaker", "{2}", "Legendary Artifact Creature — Food Knight", """
        Sir Pepper has trample, hexproof, and haste as long as an opponent controls a planeswalker.
        Whenever another artifact you control is put into a graveyard from the battlefield, put a +1/+1 counter on Sir Pepper and scry 1.
        {2}, {T}, Sacrifice Sir Pepper: You gain life equal to its power.
    """.trimIndent(), "3/1")
    private val mara = Cards.invent("Whispering Mara", "{2}{B}", "Legendary Planeswalker — Mara", "[+1]: You gain 1 life.", null, "3")
    private val trinket = Cards.invent("Pewter Keepsake", "{1}", "Artifact", "{T}, Sacrifice this artifact: You gain 1 life.")
    private val terror = Cards.invent("Futile Terror", "{B}", "Enchantment", """
        When this enchantment enters, each opponent discards a card and loses 2 life.
        When this enchantment is put into a graveyard from the battlefield, scry 2.
        {2}{B}: Sacrifice this enchantment.
    """.trimIndent())

    /** Resolves Dive into Autumn: its scry 1 (nothing to the bottom) and draw, then the trigger the tap caused is on the Stack. */
    private fun Scenario.afterDive() {
        resolveAll()
        while (pending is Decision.ChooseObjects) { answer(Answer.Objects(emptyList())); resolveAll() }
    }

    /** Pays {1} for Hilvara's trigger (answered yes) and chooses mode [mode], then resolves what is left. */
    private fun Scenario.hilvaraTrigger(mode: Int) {
        afterDive()
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        decision<Decision.Modes>()
        answer(Answer.Modes(listOf(mode)))
        playOut()
    }

    // CR 701.26a: Alice's own effect taps an opponent's untapped creature; 603.12 reflexive trigger, mode 1: a 4/4 token (CR 111.1)
    @Test
    fun hilvaraTappingAnOpponentsCreatureMakesAnElemental() = scenario(players = 3) {
        alice.battlefield(hilvara, "Plains", "Plains", "Plains")
        bob.battlefield(lurker)
        alice.hand(dive)
        alice.library("Forest", "Forest")
        alice.casts(dive, target = "Thornpath Lurker")
        hilvaraTrigger(0)
        val token = tokens("Elemental Token").single()
        assertEquals(4, state.obj(token)!!.let { view()[token]!!.characteristics!!.power })
        expect { lurker.tapped shouldBe true }
    }

    // CR 122.1, mode 2: a +1/+1 counter on each creature Alice controls (not the opponent's)
    @Test
    fun hilvaraCountersOnEachOfYourCreatures() = scenario(players = 3) {
        alice.battlefield(hilvara, sprite, "Plains", "Plains", "Plains")
        carol.battlefield(lurker)
        alice.hand(dive)
        alice.library("Forest", "Forest")
        alice.casts(dive, target = "Thornpath Lurker")
        hilvaraTrigger(1)
        expect {
            hilvara.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            sprite.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            lurker.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 701.22a, 121.1, mode 3: scry 2, then draw a card
    @Test
    fun hilvaraScriesThenDraws() = scenario(players = 3) {
        alice.battlefield(hilvara, "Plains", "Plains", "Plains")
        bob.battlefield(lurker)
        alice.hand(dive)
        alice.library("Forest", "Swamp", "Island", "Island")
        alice.casts(dive, target = "Thornpath Lurker")
        hilvaraTrigger(2)
        // the spell drew the top card and the mode drew the next one
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 118.12: declining the optional payment makes no reflexive trigger
    @Test
    fun hilvaraDoesNothingWhenThePaymentIsDeclined() = scenario(players = 3) {
        alice.battlefield(hilvara, "Plains", "Plains", "Plains")
        bob.battlefield(lurker)
        alice.hand(dive)
        alice.library("Forest", "Forest")
        alice.casts(dive, target = "Thornpath Lurker")
        afterDive()
        answer(Answer.YesNo(false))
        playOut()
        assertEquals(0, tokens("Elemental Token").size)
        expect { hilvara.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 701.26a: an opponent tapping their own creature, or Alice tapping her own, isn't "you tap an untapped creature an opponent controls"
    @Test
    fun hilvaraIgnoresCreaturesTappedByOthersOrOwn() = scenario(players = 3) {
        alice.battlefield(hilvara, sprite, "Plains", "Plains", "Plains")
        bob.battlefield(lurker, "Plains", "Plains")
        bob.hand(dive)
        bob.library("Forest", "Forest")
        alice.hand(dive)
        alice.library("Forest", "Forest")
        turn(number = 2, active = bob)
        bob.casts(dive, target = "Thornpath Lurker")
        afterDive()
        assertTrue(pending == null, "Hilvara triggered for Bob tapping his own creature")
        assertEquals(0, tokens("Elemental Token").size)
    }

    @Test
    fun hilvaraIgnoresYourOwnCreatureBeingTapped() = scenario(players = 3) {
        alice.battlefield(hilvara, sprite, "Plains", "Plains", "Plains")
        alice.hand(dive)
        alice.library("Forest", "Forest")
        alice.casts(dive, target = "Lantern Sprite")
        afterDive()
        assertTrue(pending == null, "Hilvara triggered for Alice's own creature")
        expect { sprite.tapped shouldBe true }
    }

    // CR 205.1b, 611.2a, 604.3a: a land that becomes a creature until end of turn, with first strike during its controller's turn only
    @Test
    fun unquietPinnacleBecomesAFirstStrikeCreatureOnYourTurn() = scenario {
        alice.battlefield(pinnacle, "Island", "Mountain")
        alice.activates(pinnacle, ability = 1)
        resolveAll()
        expect {
            pinnacle.types.contains(CardType.Creature) shouldBe true
            pinnacle.types.contains(CardType.Land) shouldBe true
            pinnacle.power shouldBe 2
            pinnacle.toughness shouldBe 1
            pinnacle hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 611.2a: during an opponent's turn "During your turn" is false, so no first strike
    @Test
    fun unquietPinnacleLacksFirstStrikeOnAnOpponentsTurn() = scenario {
        alice.battlefield(pinnacle, "Island", "Mountain")
        turn(number = 2, active = bob, priority = alice)
        alice.activates(pinnacle, ability = 1)
        resolveAll()
        expect {
            pinnacle.types.contains(CardType.Creature) shouldBe true
            pinnacle.power shouldBe 2
            pinnacle lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 208.1, 613.4c: +1/+1 for each enchantment you control, counted as the ability resolves
    @Test
    fun drowsingWallguardGrowsWithEnchantments() = scenario {
        alice.battlefield(wallguard, ward, rune, "Plains", "Plains", "Plains")
        bob.battlefield(mossAura)
        alice.activates(wallguard, ability = 0)
        resolveAll()
        expect {
            wallguard.power shouldBe 3
            wallguard.toughness shouldBe 3
        }
    }

    // CR 707.2: a copy of a legendary enchantment that isn't legendary; no Aura, so Yarrow stays tapped
    @Test
    fun yarrowCopiesAnEnchantmentWithoutLegendary() = scenario {
        alice.battlefield(yarrow, ward, "Plains", "Plains")
        alice.activates(yarrow, ability = 0, target = "Quilted Ward")
        playOut()
        val copy = state.battlefield.first { id -> state.obj(id)?.token == true }
        val c = view()[copy]!!.characteristics!!
        assertEquals("Quilted Ward", c.name)
        assertTrue(Supertype.Legendary !in c.supertypes)
        assertTrue(Supertype.Legendary in view()[card("Quilted Ward")]!!.characteristics!!.supertypes)
        expect { yarrow.tapped shouldBe true }
    }

    // CR 201.2a: a permanent with the same name as another permanent you control can't be chosen; one with a unique name can
    @Test
    fun yarrowCantChooseAnEnchantmentSharingANameWithAnotherPermanent() = scenario {
        alice.battlefield(yarrow, rune, rune, ward, "Plains", "Plains")
        alice.activates(yarrow, ability = 0)
        val options = decision<Decision.Targets>().options
        assertEquals(listOf(target("Quilted Ward")), options)
    }

    // CR 303.4f: the token Aura is put onto the battlefield attached to a creature; "If the token is an Aura" untaps Yarrow and scries 2
    @Test
    fun yarrowUntapsAndScriesWhenTheTokenIsAnAura() = scenario {
        alice.battlefield(yarrow, mossAura, lurker, "Plains", "Plains")
        alice.library("Forest", "Swamp", "Island")
        attach("Moss Aura", "Thornpath Lurker")
        alice.activates(yarrow, ability = 0, target = "Moss Aura")
        playOut("Thornpath Lurker")
        assertEquals(1, state.battlefield.count { id -> state.obj(id)?.token == true })
        expect { yarrow.tapped shouldBe false }
    }

    // CR 608.2h: the life gained is Sir Pepper's power as it last existed (3 + 2 counters), after being sacrificed as a cost
    @Test
    fun sirPepperGainsLifeEqualToItsPowerAsItLastExisted() = scenario(players = 3) {
        alice.battlefield(pepper, "Plains", "Plains", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.activates(pepper, ability = 0)
        resolveAll()
        expect {
            pepper.zone shouldBe Zone.Graveyard
            alice.life shouldBe 25
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 700.4: another artifact put into a graveyard from the battlefield: a +1/+1 counter and scry 1
    @Test
    fun sirPepperGrowsWhenAnotherArtifactGoesToTheGraveyard() = scenario {
        alice.battlefield(pepper, trinket, "Plains", "Plains")
        alice.library("Forest", "Swamp")
        alice.activates(trinket, ability = 0)
        playOut()
        expect {
            trinket.zone shouldBe Zone.Graveyard
            pepper.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 611.3a / 613: trample, hexproof and haste as long as any opponent controls a planeswalker (three players)
    @Test
    fun sirPepperHasKeywordsWhileAnOpponentControlsAPlaneswalker() = scenario(players = 3) {
        alice.battlefield(pepper)
        carol.battlefield(mara, counters = listOf(CounterKind.Loyalty to 3))
        expect {
            pepper hasKeyword Keyword.Trample.kind
            pepper hasKeyword Keyword.Hexproof.kind
            pepper hasKeyword Keyword.Haste.kind
        }
    }

    @Test
    fun sirPepperLacksKeywordsWhenOnlyYouControlAPlaneswalker() = scenario(players = 3) {
        alice.battlefield(pepper, mara, counters = listOf(CounterKind.Loyalty to 3))
        expect {
            pepper lacksKeyword Keyword.Trample.kind
            pepper lacksKeyword Keyword.Hexproof.kind
            pepper lacksKeyword Keyword.Haste.kind
        }
    }

    // CR 102.1, 701.9a: each of two opponents discards a card and loses 2 life; CR 700.4: sacrificed, it scries 2
    @Test
    fun futileTerrorMakesEachOpponentDiscardAndLoseLife() = scenario(players = 3) {
        alice.hand(terror)
        alice.battlefield("Swamp")
        bob.hand("Plains")
        carol.hand("Island")
        alice.casts(terror)
        playOut()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 20
            bob.hand.size shouldBe 0
            carol.hand.size shouldBe 0
        }
    }

    @Test
    fun futileTerrorScriesWhenItGoesToTheGraveyard() = scenario {
        alice.battlefield(terror, "Swamp", "Swamp", "Swamp")
        alice.library("Forest", "Swamp", "Island")
        alice.activates(terror, ability = 0)
        playOut()
        expect { terror.zone shouldBe Zone.Graveyard }
        assertTrue(pending == null)
    }
}
