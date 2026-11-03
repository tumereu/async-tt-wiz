package arcanum.engine.sets.tmt

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Teenage Mutant Ninja Turtles cards in play: their new wordings, from counters put plus one to a wheel of hand and graveyard. */
class TmtCardsScenariosTest {
    private val bear = Cards.invent("Sewer Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Sewer Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val zap = Cards.invent("Sewer Zap", "{R}", "Instant", "Sewer Zap deals 2 damage to any target.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val mirei = Cards.invent("Mirei, Heir of the Clan", "{1}{W}{B}", "Legendary Creature — Human Ninja", """
        Sneak {2}{W}{B} (You may cast this spell for {2}{W}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. She enters tapped and attacking.)
        Whenever Mirei deals combat damage to a player, return target creature card from your graveyard to your hand. If her sneak cost was paid this turn, instead return that card to the battlefield.
    """, "3/3")
    private val zany = Cards.invent("Zany, Ooze Enthusiast", "{1}{G}", "Legendary Creature — Mutant Ninja Turtle", """
        When Zany enters, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
        If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on it instead.
    """, "1/1")
    private val gloop = Cards.invent("Gloop, Living Sludge", "{X}{G}{G}", "Legendary Creature — Ooze Mutant", """
        Trample
        Activated abilities of artifact tokens you control cost {1} less to activate.
        When Gloop enters, create X Mutagen tokens. (They're artifacts with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
    """, "2/3")
    private val kumiho = Cards.invent("Kumiho, Dragon's Daughter", "{4}{U}{U}", "Legendary Creature — Fox Warlock Avatar", """
        Vigilance
        Whenever Kumiho enters or deals combat damage to a player, you may exchange control of two other target creatures controlled by different players.
    """, "6/6")
    private val hotshell = Cards.invent("Hotshell, Ninja Destroyer", "{2}{R}{R}", "Legendary Creature — Mutant Ninja Turtle", """
        Hotshell must be blocked if able.
        Enrage — Whenever Hotshell is dealt damage, add that much {R}. Until end of turn, you don't lose this mana as steps and phases end.
    """, "4/4")
    private val quillon = Cards.invent("Quillon, Drain Samurai", "{3}{W}", "Legendary Creature — Mutant Ninja Turtle Samurai", """
        Sneak {2}{W}{W}
        Double strike
        During your turn, you may cast creature spells with power or toughness 1 or less from your graveyard. If you cast a spell this way, that creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
    """, "3/3")
    private val squire = Cards.invent("Sewer Squire", "{W}", "Creature — Human Soldier", "", "1/1")
    private val vats = Cards.invent("The Cloning Vats", "{4}{B}{B}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Exile target creature card from your graveyard. Create a token that's a copy of it, except it isn't legendary and is a Mutant in addition to its other types.
        II, III — Create a token that's a copy of a card exiled with this Saga, except it isn't legendary and is a Mutant in addition to its other types.
    """)
    private val legend = Cards.invent("Old Sewer Legend", "{2}{G}", "Legendary Creature — Rat", "", "3/3")
    private val snap = Cards.invent("Snap & Snarl, Terrible Twos", "{B/R}{B/R}", "Legendary Creature — Turtle Wolf Mutant", """
        This spell can't be countered.
        Menace
        Whenever a player casts a spell, if the amount of mana spent to cast it was less than its mana value, Snap & Snarl deal 3 damage to that player.
    """, "3/2")
    private val inventor = Cards.invent("Inventor's Technique", "{2}{U}", "Sorcery", """
        Sneak {U} (You may cast this spell for {U} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Draw two cards.
    """)
    private val tusk = Cards.invent("Tusk & Horn", "{1}{B/G}{B/G}", "Legendary Creature — Boar Rhino Mutant",
        "Whenever Tusk & Horn attack or block, sacrifice a permanent unless you discard a card.", "7/5")
    private val scrapper = Cards.invent("Scrapper Tom, Alleycat Blues", "{4}{R}", "Legendary Creature — Cat Mutant Rebel", """
        At the beginning of combat on your turn, create a 2/2 red Mutant creature token. It gains haste until end of turn. Destroy it at the beginning of the next end step.
        {1}{W}: Target attacking creature token gains indestructible until end of turn.
    """, "4/4")
    private val crushed = Cards.invent("Crushed by the Clan", "{1}{B}", "Instant", """
        Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
        Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -5/-5 until end of turn instead.
    """)
    private val sludge = Cards.invent("The Primordial Sludge", "{2}", "Legendary Artifact", """
        Whenever a creature you control with a +1/+1 counter on it leaves the battlefield, create a Mutagen token for each +1/+1 counter on it. (A Mutagen token is an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
        {T}: Exile target card from a graveyard. Create a Mutagen token.
    """)
    private val rivals = Cards.invent("Fists & Blades, Sibling Rivals", "{1}{R/W}{R/W}", "Legendary Creature — Mutant Ninja Turtle",
        "Whenever Fists & Blades attack, if it's the first combat phase of the turn, untap one or two target attacking creatures. After this phase, there is an additional combat phase.", "2/4")
    private val shellsInTime = Cards.invent("Shells Through Time", "{5}{U}{U}", "Sorcery", """
        Return all creatures to their owners' hands. Each player may shuffle their hand and graveyard into their library, then each player who does draws seven cards.
        Exile Shells Through Time.
    """)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.sneakOf(name: String): AbilityOrigin = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost && it.keyword == arcanum.api.Keyword.Sneak.Kind } == true
    }.origin

    private fun Scenario.unblocked(attacker: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(attacker)
        allPass()
        if (state.priority.holder == null) bob.blocks()
    }

    /** Sneaked in this turn, her combat damage trigger returns the creature card to the battlefield instead of the hand. */
    // CR 702.190a, 510.2
    @Test
    fun sneakedThisTurnReturnsToBattlefield() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Swamp", "Swamp")
        alice.graveyard(ogre)
        alice.hand(mirei)
        unblocked(bear)
        alice.casts(mirei, way = CastWay(alternative = sneakOf(mirei)), choosing = listOf(bear))
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bob.life shouldBe 17
        }
    }

    /** A Mutagen's +1/+1 counter becomes two under the replacement (CR 614.1a, 616.1). */
    @Test
    fun countersPutPlusOne() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest")
        alice.hand(zany)
        alice.casts(zany)
        resolveAll()
        alice.activates("Mutagen Token", target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    /** X Mutagen tokens, and their ability costs {1} less: activated with no mana left (CR 601.2f, 107.3m). */
    @Test
    fun mutagensCostLess() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", "Forest")
        alice.hand(gloop)
        alice.casts(gloop, x = 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Mutagen Token" } shouldBe 2 }
        alice.activates("Mutagen Token", target = bear)
        resolveAll()
        alice.activates("Mutagen Token", target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    /** Two creatures controlled by different players exchange controllers. */
    // CR 701.12a
    @Test
    fun exchangeCreaturesOfDifferentPlayers() = scenario {
        alice.battlefield(bear, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(kumiho)
        bob.battlefield(ogre)
        alice.casts(kumiho)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(bear))))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bear.controller shouldBe bob.id
            ogre.controller shouldBe alice.id
        }
    }

    /** Dealt 2 damage: two red mana, kept as the step ends. */
    // CR 106.4, 120.3
    @Test
    fun enrageAddsManaThatStays() = scenario {
        alice.battlefield(hotshell, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = hotshell)
        resolveAll()
        assertEquals(2, state.player(alice.id).manaPool.size)
        advanceTo(TurnStep.BeginningOfCombat)
        assertEquals(2, state.player(alice.id).manaPool.size)
    }

    /** A 1/1 creature card cast from the graveyard during your turn enters with a finality counter. */
    // CR 601.2a, 122.1h
    @Test
    fun smallCreatureFromGraveyardWithFinality() = scenario {
        alice.battlefield(quillon, "Plains")
        alice.graveyard(squire)
        alice.casts(squire)
        resolveAll()
        expect {
            squire.zone shouldBe Zone.Battlefield
            squire.counters(CounterKind.Named("finality")) shouldBe 1
        }
    }

    /** Chapter I exiles a legendary creature card and makes a non-legendary Mutant token copy of it. */
    // CR 707.9b, 714.2b
    @Test
    fun cloneIsntLegendaryAndIsAMutant() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(legend)
        alice.hand(vats)
        alice.casts(vats)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(legend))))
        resolveAll()
        val token = state.battlefield.first { state.obj(it)!!.token }
        val chars = view()[token]!!.characteristics!!
        assertTrue(arcanum.api.Supertype.Legendary !in chars.supertypes)
        assertTrue(arcanum.api.Subtype("Mutant") in chars.subtypes && arcanum.api.Subtype("Rat") in chars.subtypes)
        assertTrue(state.exile.any { nameOf(state.obj(it)!!) == legend })
    }

    /** Sneak costs less mana than the spell's mana value: its caster is dealt 3 damage. */
    // CR 601.2h, 202.3
    @Test
    fun cheapSpellsHurt() = scenario {
        alice.battlefield(snap, bear, "Island")
        alice.library("Island", "Island")
        alice.hand(inventor)
        unblocked(bear)
        alice.casts(inventor, way = CastWay(alternative = sneakOf(inventor)), choosing = listOf(bear))
        resolveAll()
        assertEquals(17, alice.life)
    }

    /** Not discarding, its controller sacrifices a permanent as it attacks. */
    // CR 118.12a
    @Test
    fun sacrificeUnlessDiscard() = scenario {
        alice.battlefield(tusk, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tusk)
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        resolveAll()
        if (pending != null) alice.chooses(bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
    }

    /** The token made at the beginning of combat has haste and is destroyed at the next end step. */
    // CR 603.7
    @Test
    fun tokenWithHasteDestroyedAtEndStep() = scenario {
        alice.battlefield(scrapper)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            "Mutant Token".zone shouldBe Zone.Battlefield
            "Mutant Token" hasKeyword arcanum.api.Keyword.Haste
        }
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(state.battlefield.none { state.obj(it)!!.token })
    }

    /** Kicked by sacrificing a creature: -5/-5 instead of -2/-2. */
    // CR 702.33a, 702.33d
    @Test
    fun kickedBySacrifice() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp")
        alice.hand(crushed)
        bob.battlefield(ogre)
        alice.casts(crushed, target = ogre, kicked = 1, choosing = listOf(bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
        }
    }

    /** A creature with two +1/+1 counters leaves: two Mutagen tokens. */
    // CR 603.10a
    @Test
    fun mutagensForEachCounter() = scenario {
        alice.battlefield(sludge, "Mountain", "Mountain")
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.hand(zap, zap)
        alice.casts(zap, target = bear)
        resolveAll()
        alice.casts(zap, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.battlefield.names.count { it == "Mutagen Token" } shouldBe 2
        }
    }

    /** In the first combat phase the attack trigger untaps and adds a combat phase; in that second one it does nothing. */
    // CR 500.8, 506.1
    @Test
    fun additionalCombatOnlyFromTheFirst() = scenario {
        alice.battlefield(rivals)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rivals)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(rivals))))
        resolveAll()
        expect { rivals.tapped shouldBe false }
        advanceTo(TurnStep.EndOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rivals)
        resolveAll()
        assertEquals(null, pending)
        expect { rivals.tapped shouldBe true }
    }

    /** Each player may shuffle hand and graveyard away; the one who does draws seven. */
    // CR 101.4
    @Test
    fun handAndGraveyardShuffledAway() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(shellsInTime)
        alice.library(*Array(10) { "Island" })
        bob.battlefield(ogre)
        bob.library(*Array(10) { "Mountain" })
        alice.casts(shellsInTime)
        resolveAll()
        var guard = 6
        while (pending is Decision.May && guard-- > 0) answer(Answer.YesNo(decision<Decision.May>().player == alice.id))
        resolveAll()
        expect {
            alice.hand.names.size shouldBe 7
            bob.hand.names shouldBe listOf(ogre)
            "Shells Through Time".zone shouldBe Zone.Exile
        }
    }
}
