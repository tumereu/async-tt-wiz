package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.mutate.Mutate
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.api.Designation
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Night review 2026-11-04: engine lanes 2–4 (combat costs, enlist, exert, banding, battles, mutate, epic, assist, dungeons and
 * the initiative, hidden agenda, the Ring, soulbond, haunt). Failing scenarios are ignored with their finding number in
 * `~/projects/atw-cache/night-review-2026-11-04.md`.
 */
class NightReview4LanesTest {
    private val bear = Cards.invent("NR4 Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("NR4 Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val blackBear = Cards.invent("NR4 Bog Bear", "{1}{B}", "Creature — Bear", "", "2/2")
    private val wall = Cards.invent("NR4 Stout Wall", "{1}{W}", "Creature — Wall", "Defender", "2/4")
    private val tax = Cards.invent("NR4 Wailing Bastion", "{2}{W}", "Enchantment",
        "Creatures can't attack you unless their controller pays {1} for each creature they control that's attacking you.")
    private val reclaiming = Cards.invent("NR4 Ashen Reclaiming", "{2}{B}", "Enchantment",
        "Black creatures can't attack unless their controller sacrifices a land of their choice for each black creature they control " +
            "that's attacking. (This cost is paid as attackers are declared.)")
    private val sergeant = Cards.invent("NR4 Drafting Sergeant", "{1}{W}", "Creature — Human Soldier",
        "Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)", "2/2")
    private val striker = Cards.invent("NR4 Sunscorched Duelist", "{1}{W}", "Creature — Human Warrior",
        "You may exert this creature as it attacks. When you do, it gets +1/+3 and gains lifelink until end of turn. " +
            "(An exerted creature won't untap during your next untap step.)", "2/2")
    private val hulk = Cards.invent("NR4 Mutate Brute", "{3}{G}{G}", "Creature — Beast", """
        Mutate {2}{G}
        Trample
        Whenever this creature mutates, draw a card.
    """.trimIndent(), "6/6")
    private val beetle = Cards.invent("NR4 Mutate Beetle", "{3}{G}", "Creature — Insect", """
        Mutate {G}
        Reach
    """.trimIndent(), "1/5")
    private val wolf = Cards.invent("NR4 Mutate Wolf", "{1}{G}", "Creature — Wolf", "Vigilance", "2/2")
    private val dyingWolf = Cards.invent("NR4 Mutate Dying Wolf", "{1}{G}", "Creature — Wolf", "When this creature dies, draw a card.", "2/2")
    private val clone = Cards.invent("NR4 Mirror Shapeshifter", "{3}{U}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any creature on the battlefield.", "0/0")
    private val claim = Cards.invent("NR4 Claim the Initiative", "{W}", "Instant", "You take the initiative.")
    private val undercity = Cards.invent("NR4 Undercity", null, "Dungeon — Undercity", """
        Sewer Gate — You gain 2 life. (→ Drowned Depths)
        Drowned Depths — Each opponent loses 2 life.
    """.trimIndent())
    private val troll = Cards.invent("NR4 Assist Iron Troll", "{5}", "Artifact Creature — Golem Troll",
        "Assist (Another player can pay up to {5} of this spell's cost.)", "4/4")
    private val epicX = Cards.invent("NR4 Epic Endless Toll", "{X}{B}", "Sorcery", """
        Target player loses X life.
        Epic (For the rest of the game, you can't cast spells. At the beginning of each of your upkeeps, copy this spell except for its epic ability. You may choose a new target for the copy.)
    """.trimIndent())
    private val grim = Cards.invent("NR4 Haunt Grim Visit", "{B}", "Sorcery", """
        Target player loses 1 life.
        Haunt (When this spell card is put into a graveyard after resolving, exile it haunting target creature.)
        When the creature this card haunts dies, target player loses 3 life.
    """.trimIndent())

    private fun Scenario.libraries() {
        players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") }
    }

    private fun Scenario.mutateWay(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** The mutate keyword is the card's first printed ability: the way to cast it when the view can't read the card. */
    private val firstMutate = CastWay(alternative = AbilityOrigin.Expansion(AbilityOrigin.Printed(0, 0), 0))

    private fun Scenario.place(where: String) {
        val d = pending
        assertTrue(d is Decision.ChooseOption, "Expected the on top / under choice, not $d")
        answer(Answer.Option(where))
    }

    /** [player] gains control of [name] by an effect made by hand (CR 613.1b), as in `ring/TheRingTest`. */
    private fun Scenario.gainControl(player: ScenarioPlayer, name: String) {
        val id = card(name)
        change(arcanum.engine.change.Changes.effect(state) { ts ->
            arcanum.engine.state.EffectObject(arcanum.engine.state.EffectId("nr4-control:$ts"), listOf(Mechanic.ControlledBy(arcanum.api.Players.You)),
                affects = null, locked = listOf(id), source = null, controller = player.id, timestamp = ts, createdTurn = state.turn.number,
                duration = arcanum.api.Duration.Indefinitely)
        })
    }

    private fun Scenario.declarations(player: ScenarioPlayer) = Engine.options(state, player.id).filterIsInstance<Input.DeclareAttackers>()

    // ---- Costs to attack (CR 508.1)

    // CR 508.1f, 508.1i: the chosen attackers are tapped before the costs to attack are paid, so an attacking creature without
    // vigilance can't tap for mana to pay its own tax. Alice's only mana is the attacking Elvish Seer: the attack can't be paid.
    @Test
    fun anAttackingManaCreatureCantPayItsOwnTax() = scenario {
        alice.battlefield("Elvish Seer")
        bob.battlefield(tax)
        advanceTo(TurnStep.DeclareAttackers)
        assertTrue(declarations(alice).none { d -> d.attacks.isNotEmpty() }, "No declaration with the Seer attacking is payable: ${declarations(alice)}")
        assertFailsWith<IllegalArgumentException> { alice.attacks("Elvish Seer") }
    }

    // CR 508.1j: partial payments aren't allowed. Two black creatures attacking need two lands sacrificed; with one land the
    // declaration can't be paid, so it isn't made or offered (CR 508.1d)
    @Test
    fun twoSacrificesWithOneLandCantBePaid() = scenario {
        alice.battlefield(blackBear, blackBear, "Swamp")
        bob.battlefield(reclaiming)
        advanceTo(TurnStep.DeclareAttackers)
        assertTrue(declarations(alice).none { it.attacks.size == 2 }, "Both bears attacking isn't payable: ${declarations(alice).map { it.attacks.size }}")
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(alice.id, state.battlefield.filter { state.objOrThrow(it).definition?.faces?.first()?.name == blackBear }
                .map { Attack(it, TargetRef.Player(bob.id)) }))
            var guard = 4
            while (pending is Decision.ChooseObjects && guard-- > 0) alice.chooses("Swamp")
        }
    }

    // CR 702.154a, 508.1g–j: the enlisted creature is tapped as a cost to attack, so it can't also tap for mana to pay another
    // cost to attack: tapping it twice isn't possible. Alice's only mana is the Seer she enlists.
    @Test
    fun theEnlistedCreatureCantAlsoPayTheTax() = scenario {
        alice.battlefield(sergeant, "Elvish Seer")
        bob.battlefield(tax)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(sergeant), TargetRef.Player(bob.id), enlisted = listOf(card("Elvish Seer"))))))
            val d = pending
            if (d is Decision.ManaPayment) answer(Answer.ManaPayment(d.suggestion))
        }
    }

    // ---- Enlist (CR 702.154)

    // CR 702.154a: any untapped creature without summoning sickness can be enlisted, also one that can't attack (defender)
    @Test
    fun aDefenderCanBeEnlisted() = scenario {
        alice.battlefield(sergeant, wall)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(sergeant), TargetRef.Player(bob.id), enlisted = listOf(card(wall))))))
        allPass()
        expect { sergeant.power shouldBe 4 }
    }

    // CR 702.154a, 608.2h: X is the tapped creature's power; the enlisted creature gone before the trigger resolves, its last known
    // power counts
    @Test
    fun enlistUsesLastKnownPower() = scenario {
        alice.battlefield(sergeant, ox)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(sergeant), TargetRef.Player(bob.id), enlisted = listOf(card(ox))))))
        alice.passes()
        bob.casts("Storm Spark", target = ox)
        allPass()
        expect { alice.graveyard has ox }
        allPass()
        expect { sergeant.power shouldBe 5 }
    }

    // ---- Exert (CR 701.43)

    // CR 701.43a: "you choose to have it not untap during YOUR next untap step": alice exerts it; bob gains control of it before
    // her next untap step (turn 3); that step passes, so it untaps in bob's untap step of turn 4
    @Test
    fun exertEndsWithTheExertingPlayersUntapStepAfterAControlChange() = scenario {
        libraries()
        alice.battlefield(striker)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(striker), TargetRef.Player(bob.id), paying = listOf(AbilityOrigin.Printed(0, 0))))))
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        gainControl(bob, striker)
        advanceTo(TurnStep.Upkeep, turn = 4)
        expect { striker.tapped shouldBe false }
    }

    // CR 701.43a, 302.6: exerting a creature with vigilance: it stays untapped; tapped later that turn, it doesn't untap during its
    // controller's next untap step
    @Test
    fun exertWithVigilanceStillStopsTheNextUntap() = scenario {
        val keen = Cards.invent("NR4 Keen Duelist", "{1}{W}", "Creature — Human Warrior",
            "Vigilance\nYou may exert this creature as it attacks. (An exerted creature won't untap during your next untap step.)", "2/2")
        val tapDown = Cards.invent("NR4 Tap Down", "{W}", "Instant", "Tap target creature.")
        libraries()
        alice.battlefield(keen, "Plains")
        alice.hand(tapDown)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(keen), TargetRef.Player(bob.id), paying = listOf(AbilityOrigin.Printed(0, 1))))))
        expect { keen.tapped shouldBe false }
        alice.casts(tapDown, target = keen)
        allPass()
        expect { keen.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { keen.tapped shouldBe true }
    }

    // ---- Mutate and merging (CR 702.140, 730)

    // CR 730.2e, 708.2: merged under a face-down creature the merged permanent is face down, a 2/2 with no abilities, so the
    // mutating card's abilities don't count: no trample, and "whenever this creature mutates" doesn't trigger
    @Test
    fun mergedUnderAFaceDownCreatureHasNoAbilities() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.battlefield(wolf, faceDown = true)
        alice.hand(hulk)
        alice.library("Forest", "Forest")
        alice.casts(hulk, way = firstMutate, target = wolf)
        allPass()
        place(Mutate.UNDER)
        allPass()
        val merged = card(wolf)
        val c = view()[merged]!!.characteristics!!
        assertEquals(2, c.power)
        assertFalse(c.hasKeyword(Keyword.Trample), "A face-down permanent has no abilities (CR 708.2a)")
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 730.2e: a face-down permanent made face up by a card merging on top isn't turned face up: its "when turned face up"
    // ability doesn't trigger
    @Test
    fun mergingOnTopOfAFaceDownCreatureDoesntTurnItFaceUp() = scenario {
        val shy = Cards.invent("NR4 Shy Grower", "{2}{G}", "Creature — Elf", """
            When this creature is turned face up, draw a card.
            Morph {1}{G}
        """.trimIndent(), "2/2")
        alice.battlefield("Forest", "Forest")
        alice.battlefield(shy, faceDown = true)
        alice.hand(beetle)
        alice.library("Forest", "Forest")
        alice.casts(beetle, way = firstMutate, target = shy)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        expect {
            beetle.power shouldBe 1
            alice.hand.size shouldBe 0
        }
    }

    // CR 707.2, 730.2a: the merge is a copiable effect, so a copy of a mutated permanent copies the topmost card's characteristics and
    // the abilities of every component
    @Test
    fun aCopyOfAMutatedPermanentCopiesTheMerge() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest", wolf)
        alice.hand(hulk)
        alice.library("Forest", "Forest")
        bob.battlefield("Island", "Island", "Island", "Island")
        bob.hand(clone)
        alice.casts(hulk, way = mutateWay(hulk), target = wolf)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(clone)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) bob.chooses(hulk)
        allPass()
        val copy = state.battlefield.first { state.objOrThrow(it).owner == bob.id && state.objOrThrow(it).definition?.faces?.first()?.name == clone }
        val c = view()[copy]!!.characteristics!!
        assertEquals(hulk, c.name)
        assertEquals(6, c.power)
        assertTrue(c.hasKeyword(Keyword.Trample))
        assertTrue(c.hasKeyword(Keyword.Vigilance), "The copy has the abilities of every component (CR 702.140e, 707.2)")
    }

    // CR 730.2a, 613.7: the merge is a copy effect with the timestamp of the merge; a copy effect applied to the merged permanent
    // later wins: it becomes a copy of the Ox, with none of the merged cards' characteristics or abilities
    @Test
    fun aLaterCopyEffectOverridesTheMerge() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", wolf)
        alice.hand(hulk)
        alice.library("Forest", "Forest")
        bob.battlefield(ox)
        alice.casts(hulk, way = mutateWay(hulk), target = wolf)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        val merged = card(wolf)
        val model = card(ox)
        change(arcanum.engine.change.Changes.effect(state) { ts ->
            arcanum.engine.state.EffectObject(arcanum.engine.state.EffectId("nr4-copy:$ts"), listOf(Mechanic.CopyOf(arcanum.api.Objects.Bound("model"))),
                affects = null, locked = listOf(merged), source = null, controller = alice.id, timestamp = ts, createdTurn = state.turn.number,
                duration = arcanum.api.Duration.EndOfTurn,
                names = arcanum.engine.state.Bindings.EMPTY.with("model", arcanum.engine.state.NamedValue.Objects(listOf(model))))
        })
        val c = view()[merged]!!.characteristics!!
        assertEquals(ox, c.name)
        assertEquals(3, c.power)
        assertFalse(c.hasKeyword(Keyword.Trample), "the later copy effect replaces the merged values (CR 613.7)")
    }

    // CR 702.140e, 603.10a: a mutated permanent has the abilities of each component; the lower card's "when this creature dies"
    // triggers as the merged permanent dies
    @Test
    fun aLowerComponentsDiesTriggerTriggers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Mountain", dyingWolf)
        alice.hand(hulk, "Storm Spark")
        alice.library("Forest", "Forest", "Forest")
        alice.casts(hulk, way = mutateWay(hulk), target = dyingWolf)
        allPass()
        place(Mutate.UNDER)
        allPass()
        // the hulk under: a 2/2 with trample and the dies trigger
        alice.casts("Storm Spark", target = dyingWolf)
        allPass()
        allPass()
        expect {
            alice.graveyard has dyingWolf
            alice.graveyard has hulk
            alice.hand.size shouldBe 2
        }
    }

    // CR 730.2c: the merged permanent is the same object: a creature controlled since the turn began can still attack
    @Test
    fun theMergedPermanentIsntSummoningSick() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", wolf)
        alice.hand(hulk)
        alice.library("Forest", "Forest")
        alice.casts(hulk, way = mutateWay(hulk), target = wolf)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hulk)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 14 }
    }

    // ---- The initiative (CR 726)

    // CR 726.4: if the player who has the initiative leaves the game, the active player takes it at once (and, taking it,
    // ventures into Undercity, CR 726.2)
    @Test
    fun theInitiativePassesToTheActivePlayerWhenItsHolderLeaves() = scenario(players = 3) {
        alice.outside(undercity)
        bob.outside(undercity)
        bob.battlefield("Plains")
        bob.hand(claim)
        alice.passes()
        bob.casts(claim)
        passUntilEmpty()
        assertTrue(Designation.Initiative in state.player(bob.id).designations)
        perform(Input.Concede(bob.id))
        assertTrue(Designation.Initiative in state.player(alice.id).designations, "alice, the active player, takes the initiative (CR 726.4)")
    }

    // ---- Assist (CR 702.132)

    // CR 702.132a, 106.6: the assisting player pays part of the spell's total cost, spending their mana to cast it: mana that may
    // only be spent to cast artifact spells pays for an artifact spell with assist
    @Test
    fun assistingWithManaRestrictedToArtifactSpells() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(troll)
        bob.battlefield("Tinker's Atelier")
        alice.casts(troll, option = "bob")
        bob.answers(Answer.Number(3))
        val bobPays = pending
        assertTrue(bobPays is Decision.ManaPayment && bobPays.player == bob.id, "bob pays: $bobPays")
        assertTrue(bobPays.suggestion.isNotEmpty(), "The Atelier's mana pays for an artifact spell: $bobPays")
        answer(Answer.ManaPayment(bobPays.suggestion))
        val alicePays = pending
        assertTrue(alicePays is Decision.ManaPayment && alicePays.player == alice.id, "alice pays the rest: $alicePays")
        answer(Answer.ManaPayment(alicePays.suggestion))
        allPass()
        expect { alice.battlefield has troll }
    }

    // ---- Epic (CR 702.50)

    // CR 702.50a, 707.10: the upkeep copy copies the spell's choices, X among them
    @Test
    fun epicCopiesKeepX() = scenario {
        libraries()
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(epicX)
        alice.casts(epicX, target = "bob", x = 2)
        allPass()
        expect { bob.life shouldBe 18 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        var guard = 6
        while (guard-- > 0 && (state.stack.isNotEmpty() || pending != null)) when (pending) {
            is Decision.May -> answer(Answer.YesNo(false))
            null -> allPass()
            else -> break
        }
        expect { bob.life shouldBe 16 }
    }

    // CR 702.50b: once a spell with epic resolves its controller can't cast spells, but still plays lands and activates abilities
    @Test
    fun epicStillAllowsLandsAndAbilities() = scenario {
        libraries()
        alice.battlefield("Swamp", "Swamp", "Swamp", "Elvish Seer")
        alice.hand(epicX, "Forest")
        alice.casts(epicX, target = "bob", x = 1)
        allPass()
        val options = Engine.options(state, alice.id)
        assertTrue(options.any { it is Input.PlayLand }, "Lands are still played: $options")
        assertTrue(options.any { it is Input.Activate && it.source == card("Elvish Seer") }, "Abilities are still activated: $options")
    }

    // ---- Haunt (CR 702.55)

    // CR 702.55a: haunt on a spell triggers only when it is put into a graveyard during its resolution: countered, it stays in the
    // graveyard
    @Test
    fun aCounteredHauntSpellDoesntHaunt() = scenario {
        alice.battlefield("Swamp")
        alice.hand(grim)
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        bob.battlefield(bear)
        alice.casts(grim, target = "bob")
        alice.passes()
        bob.casts("Denial", target = grim)
        allPass()
        allPass()
        expect {
            alice.graveyard has grim
            stack.size shouldBe 0
        }
        assertTrue(pending == null, "No haunt trigger asks for a target: $pending")
    }

    // ---- Hidden agenda (CR 702.106)

    // CR 702.106a, b, D60: in a full-information state the host must not show alice's face-down conspiracy (nor its chosen name)
    // to bob; only its owner may turn it face up
    @Test
    fun aFaceDownConspiracyIsHiddenFromOthers() = scenario {
        val tithe = Cards.invent("NR4 Agenda Secret Tithe", null, "Conspiracy", """
            Hidden agenda (Start the game with this conspiracy face down in the command zone and secretly choose a card name. You may turn this conspiracy face up any time and reveal that name.)
            Spells with the chosen name you cast cost {1} less to cast.
        """.trimIndent())
        val id = put(alice, listOf(tithe), Zone.Command, faceDown = true).single()
        state.objects[id] = state.objOrThrow(id).copy(memory = arcanum.engine.state.Bindings.EMPTY.with(
            arcanum.engine.conspiracy.Conspiracies.CHOSEN_NAME, arcanum.engine.state.NamedValue.Name(bear)))
        assertTrue(id in Host.visible(state, alice.id), "Its owner may look at it")
        assertFalse(id in Host.visible(state, bob.id), "bob doesn't know it")
        assertFailsWith<IllegalArgumentException> {
            perform(Input.Activate(bob.id, id, AbilityOrigin.Expansion(AbilityOrigin.Printed(0, 0), 0)))
        }
    }
}
