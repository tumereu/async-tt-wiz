package arcanum.engine.review

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2: cube cards in combination, played out as a player would, checked against the rules.
 */
class Review2CombosTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private val minusOne = CounterKind.PowerToughness(-1, -1)

    /** Velo sacrificed to Renderer's Altar: its dies trigger deals damage equal to its power as it last existed
     * (1/1 with two +1/+1 counters: 3). */
    // CR 603.10a, 608.2h
    @Test
    fun veloSacrificedDealsItsLastPower() = scenario {
        alice.battlefield("Renderer's Altar")
        alice.battlefield("Velo, Master of Ceremonies", counters = listOf(plusOne to 2))
        alice.activates("Renderer's Altar", choosing = listOf("Velo, Master of Ceremonies"))
        // "Whenever you sacrifice a permanent" looks back too (CR 603.10a): both of Velo's abilities trigger
        answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        repeat(2) { allPass() }
        expect { bob.life shouldBe 17 }
    }

    /** Havoc Imp with Cursed Pillager: each player sacrifices a creature, so the Imp triggers twice. */
    // CR 603.2, 701.21
    @Test
    fun havocImpTriggersForEachSacrifice() = scenario {
        alice.battlefield("Havoc Imp", "Elvish Seer", "Swamp", "Swamp")
        alice.hand("Cursed Pillager")
        bob.battlefield("Brakka, Wild Whelp")
        alice.casts("Cursed Pillager")
        allPass()
        allPass()
        // alice chooses what to sacrifice among her nontoken creatures
        while (pending is Decision.ChooseObjects) alice.chooses("Elvish Seer")
        var guard = 10
        while (guard-- > 0 && (pending is Decision.Targets || state.stack.isNotEmpty())) {
            if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob")))) else allPass()
        }
        expect {
            bob.graveyard has "Brakka, Wild Whelp"
            alice.graveyard has "Elvish Seer"
            bob.life shouldBe 18
        }
    }

    /** Mender of Afflictions with persist: the creature returns with no -1/-1 counter (one minus one), so it can
     * persist again and again. */
    // CR 702.79a, 614.1a
    @Test
    fun menderMakesPersistRepeat() = scenario {
        alice.battlefield("Mender of Afflictions", "Festering Gremlin")
        alice.battlefield("Renderer's Altar")
        alice.activates("Renderer's Altar", choosing = listOf("Festering Gremlin"))
        allPass()
        allPass()
        expect {
            alice.battlefield has "Festering Gremlin"
            "Festering Gremlin".counters(minusOne) shouldBe 0
        }
        alice.activates("Renderer's Altar", choosing = listOf("Festering Gremlin"))
        allPass()
        allPass()
        expect { alice.battlefield has "Festering Gremlin" }
    }

    /** Elenwyn with persist: the returning creature gets a -1/-1 counter and Elenwyn's +1/+1 counter; they annihilate,
     * leaving none, so it can persist again. */
    // CR 702.79a, 704.5q, 614.1c
    @Test
    fun elenwynAndPersistCountersAnnihilate() = scenario {
        alice.battlefield("Elenwyn, Spinner of Dawn", "Festering Gremlin", "Renderer's Altar")
        alice.activates("Renderer's Altar", choosing = listOf("Festering Gremlin"))
        allPass()
        allPass()
        expect {
            alice.battlefield has "Festering Gremlin"
            "Festering Gremlin".counters(minusOne) shouldBe 0
            "Festering Gremlin".counters(plusOne) shouldBe 0
            "Festering Gremlin".power shouldBe 2
        }
    }

    /** Nutcracker and storm: each copy of Chitterstorm creates its Squirrel plus one more. */
    // CR 614.1a, 702.40a
    @Test
    fun nutcrackerDoublesEachStormCopy() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal", "Forest", "Forest", "Mountain")
        alice.hand("Storm Spark", "Chitterstorm")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts("Chitterstorm")
        repeat(3) { allPass() }
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 4 }
    }

    /** Rod of the Fabulist: "one or more creature tokens" once for Nutcracker's batch (the Elementals and the
     * Squirrels are created together). */
    // CR 603.2c, 614.1a
    @Test
    fun rodCountsOneBatchOfTokens() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal", "Rod of the Fabulist")
        alice.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        alice.activates("Ignia, Acolyte of Embers", ability = 1)
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == "Elemental Token" } shouldBe 2
            alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2
            "Rod of the Fabulist".counters(CounterKind.Named("story")) shouldBe 1
        }
    }

    /** Brassel prevents combat damage to an attacking artifact creature: it survives a block by a bigger creature. */
    // CR 615.1, 510.2
    @Test
    fun brasselProtectsAttackingArtifacts() = scenario {
        alice.battlefield("Brassel, Gearwork Sage", "Volatile Myr")
        bob.battlefield("Brakka, Wild Whelp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Volatile Myr")
        allPass()
        bob.blocks("Brakka, Wild Whelp" to "Volatile Myr")
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.battlefield has "Volatile Myr" }
    }

    /** Endless Return and dredge: two replacement effects for one draw; the player chooses (dredge: mill three and
     * return Shadowsting). */
    // CR 616.1, 702.52a
    @Test
    fun endlessReturnAndDredgeLetThePlayerChoose() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.battlefield("Endless Return")
        alice.graveyard("Shadowsting")
        alice.library("Elvish Seer", "Island", "Island", "Island")
        alice.passes()
        bob.passes()
        // The draw step's draw: two replacements
        val d = pending
        assertTrue(d is Decision.ChooseReplacement || d is Decision.May, "a choice of replacement: $d")
    }

    /** Gloomwing Skulker's "any player may activate this ability": bob may activate alice's Skulker, discarding his
     * own card. */
    // CR 602.2
    @Test
    fun anyPlayerMayActivateTheSkulker() = scenario {
        alice.battlefield("Gloomwing Skulker")
        bob.hand("Island")
        turn(number = 1, active = alice, priority = bob)
        val inputs = Engine.options(state, bob.id).filterIsInstance<Input.Activate>().filter { it.source == card("Gloomwing Skulker") }
        assertEquals(1, inputs.size, "bob is offered the Skulker's ability")
        perform(inputs.single())
        if (pending is Decision.ChooseObjects) bob.chooses("Island")
        allPass()
        expect {
            "Gloomwing Skulker".power shouldBe 1
            bob.graveyard has "Island"
        }
    }

    /** Wasted Bounty: sacrificing a dual land adds one mana of a type it could produce. */
    // CR 106.7
    @Test
    fun wastedBountySacrificesALand() = scenario {
        alice.battlefield("Wasted Bounty", "Smoldering Isle")
        alice.activates("Wasted Bounty", choosing = listOf("Smoldering Isle"), color = Color.Red)
        if (pending is Decision.ChooseColor) alice.answers(Answer.ColorChoice(Color.Red))
        expect { alice.graveyard has "Smoldering Isle" }
        assertEquals(listOf(Color.Red), alice.pool.map { it.color })
    }

    /** Pyrrhus counts hybrid {B/R} pips toward devotion to red. */
    // CR 700.5
    @Test
    fun pyrrhusCountsHybridDevotion() = scenario {
        alice.battlefield("Pyrrhus, Tempered in Ash", "Bloodthirsty Redcap")
        expect { "Pyrrhus, Tempered in Ash".power shouldBe 4 }
    }

    /** Ossira: casting a legendary (historic) artifact returns a creature card from the graveyard. */
    // CR 700.6, 603.2
    @Test
    fun ossiraReturnsOnAHistoricSpell() = scenario {
        alice.battlefield("Ossira, Ancestral Herald")
        alice.hand("Opaline Trinket")
        alice.graveyard("Elvish Seer")
        alice.casts("Opaline Trinket")
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        repeat(2) { allPass() }
        expect { alice.battlefield has "Elvish Seer" }
    }

    /** Smoldering Spite in the graveyard with a Mountain: a creature that just arrived can attack. */
    // CR 113.6b, 702.10
    @Test
    fun smolderingSpiteGivesHasteFromTheGraveyard() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield("Elvish Seer", sick = true)
        alice.graveyard("Smoldering Spite")
        assertTrue(!view()[card("Elvish Seer")]!!.summoningSick || view()[card("Elvish Seer")]!!.characteristics!!.hasKeyword(arcanum.api.Keyword.Haste))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 19 }
    }

    /** Duskflame Phoenix returns at the beginning of combat after two creatures died this turn. */
    // CR 603.4, 603.6
    @Test
    fun duskflamePhoenixReturns() = scenario {
        alice.battlefield("Renderer's Altar", "Elvish Seer", "Thicketwood Elves")
        alice.graveyard("Duskflame Phoenix")
        alice.activates("Renderer's Altar", choosing = listOf("Elvish Seer"))
        alice.activates("Renderer's Altar", choosing = listOf("Thicketwood Elves"))
        advanceTo(TurnStep.BeginningOfCombat)
        resolveStack()
        expect { alice.battlefield has "Duskflame Phoenix" }
    }

    /** Origin Vault's intervening "if this artifact is untapped": a nontoken creature entering makes a Myr. */
    // CR 603.4
    @Test
    fun originVaultMakesAMyr() = scenario {
        alice.battlefield("Origin Vault", "Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        repeat(2) { allPass() }
        expect { alice.battlefield.names.count { it == "Myr Token" } shouldBe 1 }
    }

    /** Evoke and flicker: Seclusion evoked; in response to its sacrifice trigger, Fleeting Recall flickers it. It
     * returns as a new object (its enters trigger again) and the sacrifice trigger finds nothing to sacrifice. */
    // CR 702.74a, 400.7, 603.10
    @Test
    fun evokeAndFlicker() = scenario {
        alice.battlefield("Plains")
        alice.hand("Seclusion", "Loyal Hearthhound", "Fleeting Recall")
        bob.battlefield("Brakka, Wild Whelp")
        val evoke = view()[card("Seclusion")]!!.characteristics!!.abilities
            .first { (it.ability as? arcanum.api.Ability.Static)?.mechanics?.any { m -> m is arcanum.api.Mechanic.AlternativeCost } == true }
        alice.casts("Seclusion", way = arcanum.engine.input.CastWay(alternative = evoke.origin), choosing = listOf("Loyal Hearthhound"))
        allPass()
        // Two triggers: evoke's sacrifice and the enters exile; order them so the sacrifice resolves last
        if (pending is Decision.OrderTriggers) answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Brakka, Wild Whelp"))))
        alice.casts("Fleeting Recall", target = "Seclusion")
        allPass()
        // The new Seclusion's enters trigger: no other target left but it may exile none
        var guard = 10
        while (guard-- > 0 && (pending != null || state.stack.isNotEmpty())) {
            when (pending) {
                is Decision.Targets -> answer(Answer.Targets(emptyList()))
                is Decision.OrderTriggers -> answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
                is Decision.May -> answer(Answer.YesNo(false))
                null -> allPass()
                else -> break
            }
        }
        expect { alice.battlefield has "Seclusion" }
    }

    /** Rampaging Reefshark: discarding two cards at once (Faithless Rummage) puts two counters, from one trigger. */
    // CR 603.2c
    @Test
    fun reefsharkCountsADiscardBatch() = scenario {
        alice.battlefield("Rampaging Reefshark", "Mountain")
        alice.hand("Faithless Rummage", "Island", "Island")
        alice.library("Forest", "Forest")
        alice.casts("Faithless Rummage")
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses("Island", "Island")
        allPass()
        expect { "Rampaging Reefshark".counters(plusOne) shouldBe 2 }
    }

    /** Stormflux Cistern: the second spell this turn gains 2 life. */
    // CR 603.2
    @Test
    fun stormfluxCisternCountsSpells() = scenario {
        alice.battlefield("Stormflux Cistern", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        allPass()
        alice.casts("Storm Spark", target = "bob")
        allPass()
        allPass()
        expect { alice.life shouldBe 23 }
    }

    /** Invoice Shredder connives on bob's second spell of the turn too ("a player"). */
    // CR 701.50a, 603.2
    @Test
    fun invoiceShredderOnTheOpponentsSecondSpell() = scenario {
        alice.battlefield("Invoice Shredder")
        alice.library("Island", "Forest")
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark", "Storm Spark")
        turn(number = 2, active = bob)
        bob.casts("Storm Spark", target = "alice")
        allPass()
        bob.casts("Storm Spark", target = "alice")
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses("Island")
        expect { alice.graveyard has "Island" }
    }

    /** Vizzik and modular: Scrapfed Devourer dies, its modular trigger puts its counter on Vizzik, plus one. */
    // CR 702.43a, 614.1a
    @Test
    fun vizzikAddsToModular() = scenario {
        alice.battlefield("Vizzik, the Glintwasp", counters = listOf(plusOne to 1))
        alice.battlefield("Scrapfed Devourer", counters = listOf(plusOne to 1))
        alice.battlefield("Renderer's Altar")
        alice.activates("Renderer's Altar", choosing = listOf("Scrapfed Devourer"))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Vizzik, the Glintwasp"))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        allPass()
        expect { "Vizzik, the Glintwasp".counters(plusOne) shouldBe 3 }
    }

    /** Spiritwrangler: a creature exiled from the battlefield (Plowshare Decree) puts a counter on it. */
    // CR 603.2, 603.6c
    @Test
    fun spiritwranglerCountsExiles() = scenario {
        alice.battlefield("Spiritwrangler", "Plains")
        alice.hand("Plowshare Decree")
        bob.battlefield("Brakka, Wild Whelp")
        alice.casts("Plowshare Decree", target = "Brakka, Wild Whelp")
        repeat(2) { allPass() }
        expect {
            "Spiritwrangler".counters(plusOne) shouldBe 1
            bob.life shouldBe 22
        }
    }

    /** Gemstone Charm's "if this artifact would enter": cast with a land card to discard, it enters; else it goes
     * to the graveyard. */
    // CR 614.1c, 614.12
    @Test
    fun gemstoneCharmWithoutALandGoesToTheGraveyard() = scenario {
        alice.hand("Gemstone Charm")
        alice.casts("Gemstone Charm")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        expect { alice.graveyard has "Gemstone Charm" }
    }

    /** Netherbound Turncoat returns from the graveyard when another creature is put into alice's graveyard from the
     * battlefield, if she pays {B}. */
    // CR 603.2, 603.6
    @Test
    fun netherboundTurncoatReturns() = scenario {
        alice.battlefield("Renderer's Altar", "Elvish Seer", "Swamp")
        alice.graveyard("Netherbound Turncoat")
        alice.activates("Renderer's Altar", choosing = listOf("Elvish Seer"))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { alice.battlefield has "Netherbound Turncoat" }
    }

    /** Plague Shamblers returns when alice casts a spell from her graveyard (flashback). */
    // CR 603.2, 702.34a
    @Test
    fun plagueShamblersOnAFlashbackSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Plague Shamblers", "Cinder Bolt")
        val flashback = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Cinder Bolt") }
        perform(flashback)
        alice.answers(Answer.Targets(listOf(target("bob"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        repeat(2) { allPass() }
        expect {
            alice.battlefield has "Plague Shamblers"
            "Plague Shamblers".tapped shouldBe true
            bob.life shouldBe 18
        }
    }

    /** Wyrm of Ashen Graves cast from the graveyard for {B}{B} and two sacrificed creatures. */
    // CR 118.9, 601.2f
    @Test
    fun wyrmOfAshenGravesFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Elvish Seer", "Thicketwood Elves")
        alice.graveyard("Wyrm of Ashen Graves")
        val cast = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Wyrm of Ashen Graves") }
        perform(cast)
        while (pending is Decision.ChooseObjects) {
            val d = decision<Decision.ChooseObjects>()
            answer(Answer.Objects(d.options.take(d.min)))
        }
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect {
            alice.battlefield has "Wyrm of Ashen Graves"
            alice.graveyard has "Elvish Seer"
            alice.graveyard has "Thicketwood Elves"
        }
    }

    /** Vengeroot returns when its controller casts their second creature spell in a turn. */
    // CR 603.4
    @Test
    fun vengerootReturnsOnTheSecondCreatureSpell() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Elvish Seer", "Thicketwood Elves")
        alice.graveyard("Vengeroot")
        alice.casts("Elvish Seer")
        allPass()
        alice.casts("Thicketwood Elves")
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        allPass()
        expect { alice.battlefield has "Vengeroot" }
    }

    /** Pilfering Nibbler returns when its owner draws their third card in a turn. */
    // CR 603.2, 121.2
    @Test
    fun pilferingNibblerOnTheThirdDraw() = scenario {
        alice.battlefield("Island")
        alice.hand("Forebear's Insight")
        alice.graveyard("Pilfering Nibbler")
        alice.library("Forest", "Forest", "Forest", "Forest")
        alice.casts("Forebear's Insight", target = "alice")
        repeat(2) { allPass() }
        expect {
            alice.battlefield has "Pilfering Nibbler"
            "Pilfering Nibbler".tapped shouldBe true
        }
    }

    /** Crimson Hexcaller and Pyrrhus: a nontoken creature dying makes a Blood token and a Satyr. */
    // CR 603.2, 603.3b
    @Test
    fun twoDeathTriggersFromOneDeath() = scenario {
        alice.battlefield("Crimson Hexcaller", "Pyrrhus, Tempered in Ash", "Elvish Seer", "Renderer's Altar")
        alice.activates("Renderer's Altar", choosing = listOf("Elvish Seer"))
        if (pending is Decision.OrderTriggers) answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
        repeat(3) { allPass() }
        expect {
            alice.battlefield has "Blood Token"
            alice.battlefield has "Satyr Token"
        }
    }

    /** Harbor Gate Tempestcaller copies the next cheap instant: Storm Spark is copied once. */
    // CR 707.10, 603.7
    @Test
    fun tempestcallerCopiesTheNextSpell() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand("Harbor Gate Tempestcaller", "Storm Spark")
        alice.casts("Harbor Gate Tempestcaller", kicked = 0)
        repeat(2) { allPass() }
        alice.casts("Storm Spark", target = "bob")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        repeat(2) { allPass() }
        expect { bob.life shouldBe 14 }
    }

    /** Persist and Endless Return trigger on the same death; alice orders them. With Endless Return's on top, the card
     * goes to her hand and persist then finds nothing to return. */
    // CR 603.3b, 400.7
    @Test
    fun persistAndEndlessReturnAreOrdered() = scenario {
        alice.battlefield("Endless Return", "Festering Gremlin", "Renderer's Altar")
        alice.activates("Renderer's Altar", choosing = listOf("Festering Gremlin"))
        val d = decision<Decision.OrderTriggers>()
        assertEquals(2, d.triggers.size)
        // The first in the order goes on the Stack first: persist first, Endless Return on top resolves first
        val persist = d.triggers.first { it.source == null || state.obj(it.source!!)?.definition?.faces?.first()?.name != "Endless Return" }
        answer(Answer.Order(listOf(persist.index) + d.triggers.map { it.index }.filter { it != persist.index }))
        repeat(3) { allPass() }
        expect {
            alice.hand has "Festering Gremlin"
            alice.battlefield lacks "Festering Gremlin"
        }
    }

    /** Sentry Klaxon: creatures don't untap in the untap step, but a creature entering untaps them all. */
    // CR 502.3, 603.2
    @Test
    fun sentryKlaxon() = scenario {
        alice.battlefield("Sentry Klaxon", "Forest")
        alice.battlefield("Elvish Seer", tapped = true)
        alice.hand("Thicketwood Elves")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { "Elvish Seer".tapped shouldBe true }
        alice.casts("Thicketwood Elves")
        repeat(2) { allPass() }
        expect { "Elvish Seer".tapped shouldBe false }
    }

    /** Baloth Sovereign enters tapped with six stun counters: each untap step removes one instead of untapping. */
    // CR 122.1d, 502.3
    @Test
    fun stunCountersReplaceUntapping() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Baloth Sovereign")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts("Baloth Sovereign")
        allPass()
        expect {
            "Baloth Sovereign".tapped shouldBe true
            "Baloth Sovereign".counters(CounterKind.Stun) shouldBe 6
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            "Baloth Sovereign".tapped shouldBe true
            "Baloth Sovereign".counters(CounterKind.Stun) shouldBe 5
        }
    }
}
