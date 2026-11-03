package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Night review 2026-11-04, second batch: battles, static copies, banding, costs to block, dungeons, conspiracies. */
class NightReview4MoreTest {
    private val bear = Cards.invent("NR4 Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("NR4 Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val tax = Cards.invent("NR4 Wailing Bastion", "{2}{W}", "Enchantment",
        "Creatures can't attack you unless their controller pays {1} for each creature they control that's attacking you.")
    private val claim = Cards.invent("NR4 Claim the Initiative", "{W}", "Instant", "You take the initiative.")
    private val undercity = Cards.invent("NR4 Undercity", null, "Dungeon — Undercity", """
        Sewer Gate — You gain 2 life. (→ Drowned Depths)
        Drowned Depths — Each opponent loses 2 life.
    """.trimIndent())
    private val siege = Cards.inventFaces("Transform",
        OracleCard("NR4 Siege of Ashfall", "{2}{R}", "Battle — Siege", "", defense = "3"),
        OracleCard("NR4 Ashfall Warden", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val blight = Cards.invent("NR4 Blight Crawler", "{1}{B}", "Creature — Phyrexian Horror", "Infect", "2/2")
    private val trampler = Cards.invent("NR4 Trampling Rhino", "{3}{G}", "Creature — Rhino", "Trample", "5/5")
    private val visionary = "Canopy Visionary"
    private val charm = Cards.invent("NR4 Mirrorbond Charm", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        Other creatures you control are copies of enchanted creature.
    """.trimIndent())
    private val bander = Cards.invent("NR4 Band Shield Knight", "{1}{W}", "Creature — Human Knight", "Banding", "2/2")
    private val flyer = Cards.invent("NR4 Band Sky Hawk", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    private val reachSpider = Cards.invent("NR4 Reaching Spider", "{1}{G}", "Creature — Spider", "Reach", "1/4")
    private val toll = Cards.invent("NR4 Toll of Coins", "{1}{W}", "Enchantment",
        "Creatures can't block unless their controller pays {1} for each blocking creature they control.")
    private val tithe = Cards.invent("NR4 Agenda Secret Tithe", null, "Conspiracy", """
        Hidden agenda (Start the game with this conspiracy face down in the command zone and secretly choose a card name. You may turn this conspiracy face up any time and reveal that name.)
        Spells with the chosen name you cast cost {1} less to cast.
    """.trimIndent())

    private val siegeDefense = listOf(CounterKind.Defense to 3)

    private fun Scenario.libraries() {
        players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") }
    }

    private fun Scenario.protectorOf(name: String) = state.obj(card(name))?.protector

    private fun Scenario.declarations(player: ScenarioPlayer) = Engine.options(state, player.id).filterIsInstance<Input.DeclareAttackers>()

    private fun Scenario.gainControl(player: ScenarioPlayer, name: String) {
        val id = card(name)
        change(arcanum.engine.change.Changes.effect(state) { ts ->
            arcanum.engine.state.EffectObject(arcanum.engine.state.EffectId("nr4-control:$ts"), listOf(Mechanic.ControlledBy(arcanum.api.Players.You)),
                affects = null, locked = listOf(id), source = null, controller = player.id, timestamp = ts, createdTurn = state.turn.number,
                duration = arcanum.api.Duration.Indefinitely)
        })
    }

    // ---- Battles (CR 310)

    // CR 115.4, 120.3h, 310.6: "any target" includes battles: damage removes defense counters; CR 310.12b: the last one removed,
    // the Siege's trigger exiles it and alice may cast it transformed
    @Test
    fun burningASiegeDefeatsIt() = scenario {
        alice.battlefield(siege, counters = siegeDefense)
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = siege)
        allPass()
        expect { stack.size shouldBe 1 }
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        passUntilEmpty()
        expect { "NR4 Ashfall Warden".zone shouldBe Zone.Battlefield }
    }

    // CR 704.5y, 310.12a: bob, its protector, gains control of alice's Siege: he can't protect his own Siege, so alice, his only
    // opponent, becomes its protector
    @Test
    fun theProtectorGainingControlOfTheSiege() = scenario {
        alice.battlefield(siege, counters = siegeDefense)
        settle()
        assertEquals(bob.id, protectorOf(siege))
        gainControl(bob, siege)
        settle()
        assertEquals(alice.id, protectorOf(siege))
    }

    // CR 310.9b: with three players, carol may attack alice's Siege that bob protects (bob is a defending player for her);
    // CR 310.9c: only bob blocks those attackers, not alice who controls it
    @Test
    fun anotherOpponentAttacksTheSiege() = scenario(players = 3) {
        libraries()
        alice.battlefield(siege, counters = siegeDefense)
        carol.battlefield(ox)
        alice.battlefield(bear)
        settle()
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        assertEquals(carol.id, state.turn.active)
        val targets = declarations(carol).flatMap { d -> d.attacks.map { it.target } }
        assertTrue(TargetRef.Object(card(siege)) in targets, "carol may attack the Siege bob protects: $targets")
        perform(Input.DeclareAttackers(carol.id, listOf(Attack(card(ox), TargetRef.Object(card(siege))))))
        allPass()
        val aliceBlocks = Engine.options(state, alice.id).filterIsInstance<Input.DeclareBlockers>()
        assertTrue(aliceBlocks.all { it.blocks.isEmpty() }, "alice, the Siege's controller, doesn't block for it: $aliceBlocks")
    }

    // CR 120.3h: damage dealt to a battle removes defense counters, whatever its source: infect gives no counters and no poison
    @Test
    fun infectDamageToABattleRemovesDefense() = scenario {
        alice.battlefield(siege, counters = siegeDefense)
        alice.battlefield(blight)
        settle()
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(blight), TargetRef.Object(card(siege))))))
        advanceTo(TurnStep.CombatDamage)
        expect {
            siege.counters(CounterKind.Defense) shouldBe 1
            siege.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 0
            bob.counters(CounterKind.Poison) shouldBe 0
        }
    }

    // CR 702.19c: a trampler attacking a battle and blocked assigns the excess to the battle
    @Test
    fun trampleOverToABattle() = scenario {
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.battlefield(trampler)
        bob.battlefield(bear)
        settle()
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(trampler), TargetRef.Object(card(siege))))))
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to trampler)
        advanceTo(TurnStep.CombatDamage)
        if (pending is Decision.Divide) answer(Answer.Division(listOf(Share(TargetRef.Object(card(bear)), 2),
            Share(TargetRef.Object(card(siege)), 3))))
        expect {
            siege.counters(CounterKind.Defense) shouldBe 2
            bob.graveyard has bear
        }
    }

    // ---- Copy effects of static abilities (CR 613.2a, 707.2)

    // CR 613.2a, 707.2, 603.6d: alice's other creatures are copies of the enchanted Canopy Visionary; a bear entering is a copy of it
    // as it enters, so the copied "when this creature enters, draw a card" triggers
    @Test
    fun aCreatureEnteringUnderAStaticCopyHasTheCopiedEntersTrigger() = scenario {
        alice.battlefield(visionary, charm, "Forest", "Forest")
        attach(charm, visionary)
        alice.hand(bear)
        alice.library("Forest", "Forest")
        alice.casts(bear)
        allPass()
        allPass()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 613.2a, 707.2, 603.6d: the other way round: Canopy Visionary entering is a copy of the enchanted bear, without its enters
    // trigger: no card drawn
    @Test
    fun aCreatureEnteringUnderAStaticCopyLosesItsOwnEntersTrigger() = scenario {
        alice.battlefield(bear, charm, "Forest", "Forest", "Forest")
        attach(charm, bear)
        alice.hand(visionary)
        alice.library("Forest", "Forest")
        alice.casts(visionary)
        allPass()
        allPass()
        expect { alice.hand.size shouldBe 0 }
    }

    // ---- Banding (CR 702.22)

    // CR 702.22c, h and its example: a band of a flyer and a creature with banding; a creature with reach blocks the flyer, so the
    // knight is blocked by it too: no damage to bob, both attackers' damage to the spider
    @Test
    fun blockingTheFlyerOfABandBlocksTheWholeBand() = scenario {
        alice.battlefield(bander, flyer)
        bob.battlefield(reachSpider)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(bander, flyer).map { Attack(card(it), TargetRef.Player(bob.id), band = 1) }))
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(reachSpider to flyer)
        advanceTo(TurnStep.CombatDamage)
        var guard = 4
        while (pending is Decision.Divide && guard-- > 0) divideAsSuggested()
        expect { bob.life shouldBe 20 }
        assertEquals(3, state.objOrThrow(card(reachSpider)).damage)
    }

    // ---- Costs to block (CR 509.1d)

    // CR 509.1d–f: blockers aren't tapped, so a mana creature may pay its own cost to block with its mana
    @Test
    fun aBlockingManaCreaturePaysItsOwnToll() = scenario {
        alice.battlefield(bear)
        bob.battlefield(toll, "Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        val blocks = Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>()
        assertTrue(blocks.any { it.blocks.isNotEmpty() }, "The Seer may block paying with its own mana: $blocks")
        perform(Input.DeclareBlockers(bob.id, listOf(Block(card("Elvish Seer"), card(bear)))))
        val d = pending
        if (d is Decision.ManaPayment) answer(Answer.ManaPayment(d.suggestion))
        assertEquals(listOf(card("Elvish Seer")), state.combat?.blockers?.map { it.id })
    }

    // CR 508.1h: "attacking you": alice attacking carol pays nothing for bob's tax
    @Test
    fun aTaxOnlyForCreaturesAttackingItsController() = scenario(players = 3) {
        alice.battlefield(bear)
        bob.battlefield(tax)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(bear), TargetRef.Player(carol.id)))))
        assertEquals(null, pending)
        assertEquals(listOf(card(bear)), state.combat?.attackers?.map { it.id })
    }

    // ---- Dungeons (CR 701.49)

    // CR 701.49d: taking the initiative while alice is in another dungeon ventures in that dungeon, not into Undercity
    @Test
    fun theInitiativeVenturesInTheCurrentDungeon() = scenario {
        val crypt = Cards.invent("NR4 Crypt", null, "Dungeon", """
            Crypt Door — You gain 1 life. (→ Bone Hall)
            Bone Hall — Each opponent loses 1 life. (→ Final Vault)
            Final Vault — You gain 3 life.
        """.trimIndent())
        val map = Cards.invent("NR4 Map", "{W}", "Sorcery", "Venture into the dungeon.")
        alice.battlefield("Plains", "Plains")
        alice.hand(map, claim)
        alice.outside(crypt, undercity)
        alice.casts(map)
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(crypt)
        passUntilEmpty()
        expect { alice.life shouldBe 21 }
        alice.casts(claim)
        passUntilEmpty()
        expect { bob.life shouldBe 19 }
        assertTrue(state.command.none { state.obj(it)?.definition?.faces?.first()?.name == undercity })
    }

    // ---- Conspiracies (CR 702.106)

    // CR 702.106e: a player leaving the game reveals their face-down conspiracies to all players
    @Test
    fun aLeavingPlayersConspiracyIsRevealed() = scenario(players = 3) {
        val id = put(bob, listOf(tithe), Zone.Command, faceDown = true).single()
        state.objects[id] = state.objOrThrow(id).copy(memory = arcanum.engine.state.Bindings.EMPTY.with(
            arcanum.engine.conspiracy.Conspiracies.CHOSEN_NAME, arcanum.engine.state.NamedValue.Name(bear)))
        val outcome = perform(Input.Concede(bob.id))
        val revealed = outcome.changes.filterIsInstance<Change.Happened>().mapNotNull { it.event as? GameEvent.Revealed }.flatMap { it.ids }
        assertTrue(id in revealed, "bob's face-down conspiracy is revealed as he leaves: $revealed")
    }

    // ---- Batch 3

    private val skystalker = Cards.invent("NR4 Bond Skystalker", "{2}{W}", "Creature — Human Soldier", """
        Soulbond
        As long as this creature is paired with another creature, both creatures have flying.
    """.trimIndent(), "2/2")
    private val specter = Cards.invent("NR4 Haunt Gloom Specter", "{1}{B}", "Creature — Spirit", """
        Haunt (When this creature dies, exile it haunting target creature.)
        When this creature enters or the creature it haunts dies, target player loses 2 life and you gain 2 life.
    """.trimIndent(), "1/1")
    private val troll = Cards.invent("NR4 Assist Bridge Troll", "{4}{G}", "Creature — Troll",
        "Assist (Another player can pay up to {4} of this spell's cost.)", "4/4")

    private fun Scenario.partner(name: String) = arcanum.engine.soulbond.Soulbond.partner(state, card(name))

    // CR 702.95c: as the soulbond trigger resolves, a creature no longer under the trigger's controller's control isn't paired
    @Test
    fun soulbondDoesntPairACreatureThatChangedControl() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(skystalker)
        alice.casts(skystalker)
        allPass()
        expect { stack.size shouldBe 1 }
        gainControl(bob, bear)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) answer(Answer.Objects((pending as Decision.ChooseObjects).options.take(1)))
        assertEquals(null, partner(skystalker), "the bear is bob's now: no pair")
    }

    // CR 702.55b, 400.7: the creature a card haunts is that object: the haunted bear leaving the battlefield and coming back is a new
    // object, whose death doesn't trigger the haunt ability
    @Test
    fun aHauntedCreatureThatLeftAndReturnedIsNoLongerHaunted() = scenario {
        alice.battlefield("Mountain", "Mountain", specter)
        alice.hand("Storm Spark", "Storm Spark")
        bob.battlefield(bear)
        alice.casts("Storm Spark", target = specter)
        allPass()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        allPass()
        expect { alice.exile has specter }
        move(bear, Zone.Hand)
        move(bear, Zone.Battlefield)
        alice.casts("Storm Spark", target = bear)
        allPass()
        assertTrue(pending == null && state.stack.isEmpty(), "No haunt trigger: ${pending ?: state.stack}")
        expect { bob.life shouldBe 20 }
    }

    // D60, P7, CR 103.5b: alice's declaration offers "Use 1: <card in her hand>"; the answers listed to bob must not name a card of
    // her hidden hand (as `options` leaves out answers naming hidden objects, an option label naming one is the same leak)
    @Test
    fun aMulliganActionDoesntNameAHiddenCardToOthers() {
        val reshuffle = Cards.invent("NR4 Ashen Reshuffle", "{2}", "Artifact",
            "Any time you could mulligan and this card is in your hand, you may exile all the cards from your hand, then draw that many " +
                "cards. (You can do this in addition to taking mulligans.)")
        val deck = arcanum.engine.setup.cards(listOf(reshuffle, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp"), "Swamp", "Undead Soothsayer")
        val mountains = arcanum.engine.setup.cards(listOf("Mountain"), "Mountain", "Glint-Tusk Corsair")
        scenario(start = arcanum.engine.setup.table(deck, mountains)) {
            random(arcanum.engine.setup.inOrder, arcanum.engine.setup.inOrder)
            perform(Input.StartGame(arcanum.engine.setup.StartOptions(startingPlayer = alice.id)))
            val d = decision<Decision.ChooseOption>()
            assertEquals(alice.id, d.player)
            assertTrue(d.options.any { reshuffle in it }, "alice is offered her card: ${d.options}")
            val leaked = Engine.options(state, bob.id).filterIsInstance<Input.Answer>()
                .mapNotNull { (it.answer as? Answer.Option)?.chosen }.filter { reshuffle in it }
            assertTrue(leaked.isEmpty(), "bob is listed answers naming alice's hidden card: $leaked")
        }
    }

    // CR 800.4f: the player chosen to assist leaves the game while paying: their part of the cost isn't paid by anybody else
    @Test
    fun theAssistingPlayerLeavesWhilePaying() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(troll)
        bob.battlefield("Island", "Island", "Island", "Island")
        carol.battlefield("Island", "Island", "Island", "Island")
        alice.casts(troll, option = "bob")
        bob.answers(Answer.Number(3))
        val bobPays = pending
        assertTrue(bobPays is Decision.ManaPayment && bobPays.player == bob.id, "bob pays: $bobPays")
        perform(Input.Concede(bob.id))
        val next = pending
        assertTrue(next !is Decision.ManaPayment || next.player != carol.id, "carol doesn't pay bob's part: $next")
        assertTrue(next !is Decision.ChoosePlayer, "nobody chooses who pays bob's part: $next")
    }
}
