package arcanum.engine.combat

import arcanum.api.Affected
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Combat (CR 506–511): declaring attackers and blockers, and combat damage with its keywords. */
class CombatTest {
    /** Invented vanilla and keyword creatures for combat (D15). */
    private fun cards() {
        Cards.invent("Plodding Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        Cards.invent("Hill Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        Cards.invent("Old Mammoth", "{4}{G}", "Creature — Elephant", "", "5/5")
        Cards.invent("Twinblade Duelist", "{1}{R}{W}", "Creature — Human Warrior", "Double strike", "2/2")
        Cards.invent("Twinblade Stomper", "{2}{R}{G}", "Creature — Beast", "Double strike, trample", "2/2")
        Cards.invent("Venomhorn Brute", "{3}{B}{G}", "Creature — Beast", "Deathtouch, trample", "4/4")
        Cards.invent("Plague Mite", "{1}{B}", "Creature — Phyrexian Insect", "Infect", "2/2")
        Cards.invent("Blight Hound", "{1}{B}", "Creature — Dog", "Wither", "2/2")
        Cards.invent("Bitter Adder", "{1}{B}", "Creature — Snake", "Toxic 2", "1/1")
    }

    /** Players pass until the declare attackers step, where the active player declares. */
    private fun Scenario.toAttackers() {
        advanceTo(TurnStep.DeclareAttackers)
    }

    /** After attackers are declared: everyone passes into the declare blockers step. */
    private fun Scenario.toBlockers() {
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
    }

    /** After blockers: everyone passes into the combat damage step. */
    private fun Scenario.toDamage() {
        allPass()
    }

    // CR 508.1a, f, k: attackers become tapped and attacking; the change says attackers were declared (CR 508.1m)
    @Test
    fun attackingTapsAndAttacks() = scenario {
        alice.battlefield("Thicketwood Elves")
        toAttackers()
        alice.attacks("Thicketwood Elves")
        expect { "Thicketwood Elves".tapped shouldBe true }
        assertEquals(listOf(TargetRef.Player(bob.id)), state.combat!!.attackers.map { it.target })
        assertTrue(applied.any { it is Change.Combat && Meaning.AttackersDeclared in it.meanings })
        // CR 508.2: the active player gets priority
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 702.20b: vigilance: attacking doesn't tap it
    @Test
    fun vigilanceDoesntTap() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way")
        toAttackers()
        alice.attacks("Velis, Mender of the Lost Way")
        expect { "Velis, Mender of the Lost Way".tapped shouldBe false }
    }

    // CR 508.1a, 302.6: a creature that came under control this turn can't attack
    @Test
    fun summoningSickCantAttack() = scenario {
        cards()
        alice.battlefield("Thicketwood Elves", sick = true)
        alice.battlefield("Plodding Bear")
        toAttackers()
        assertFailsWith<IllegalArgumentException> { alice.attacks("Thicketwood Elves") }
    }

    // CR 702.10b: haste: it attacks the turn it arrives
    @Test
    fun hasteAttacks() = scenario {
        alice.battlefield("Gearwheel Courier", sick = true)
        toAttackers()
        alice.attacks("Gearwheel Courier")
        assertEquals(1, state.combat!!.attackers.size)
    }

    // P5, CR 508.1a: with only summoning-sick creatures nothing can attack: no declaration is awaited
    @Test
    fun onlySickCreaturesDeclareNothing() = scenario {
        alice.battlefield("Thicketwood Elves", sick = true)
        toAttackers()
        assertEquals(alice.id, state.priority.holder)
        assertTrue(state.combat!!.attackers.isEmpty())
    }

    // CR 702.3b: defender can't attack
    @Test
    fun defenderCantAttack() = scenario {
        alice.battlefield("Wall of Portents", "Thicketwood Elves")
        toAttackers()
        assertFailsWith<IllegalArgumentException> { alice.attacks("Wall of Portents") }
    }

    // CR 508.1a: a tapped creature can't attack
    @Test
    fun tappedCantAttack() = scenario {
        alice.battlefield("Thicketwood Elves", "Elvish Seer", tapped = false)
        toAttackers()
        perform(Input.DeclareAttackers(alice.id, emptyList()))
        assertFailsWith<IllegalArgumentException> { alice.attacks("Thicketwood Elves") }
    }

    // CR 508.1c: "can't attack" from an effect
    @Test
    fun cantAttackEffect() = scenario {
        alice.battlefield("Thicketwood Elves", "Elvish Seer")
        effect(bob, Mechanic.CantAttack(), affects = Affected.Objects(Objects.Bound("it")), names = mapOf("it" to "Thicketwood Elves"))
        toAttackers()
        assertFailsWith<IllegalArgumentException> { alice.attacks("Thicketwood Elves") }
        alice.attacks("Elvish Seer")
    }

    // CR 508.1d: a creature that attacks each combat if able must be declared
    @Test
    fun mustAttack() = scenario {
        alice.battlefield("Thicketwood Elves", "Elvish Seer")
        effect(bob, Mechanic.MustAttack(), affects = Affected.Objects(Objects.Bound("it")), names = mapOf("it" to "Thicketwood Elves"))
        toAttackers()
        assertFailsWith<IllegalArgumentException> { alice.attacks("Elvish Seer") }
        alice.attacks("Elvish Seer", "Thicketwood Elves")
    }

    // CR 508.1c: "No more than one creature can attack each combat"
    @Test
    fun maximumAttackers() = scenario {
        alice.battlefield("Thicketwood Elves", "Elvish Seer")
        effect(bob, Mechanic.MaximumAttackers(1), affects = Affected.Objects(Objects.Each(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Creature))),
            duration = Duration.Indefinitely)
        toAttackers()
        assertFailsWith<IllegalArgumentException> { alice.attacks("Thicketwood Elves", "Elvish Seer") }
        alice.attacks("Elvish Seer")
    }

    // CR 509.1c: a creature that blocks each combat if able must block when it can
    @Test
    fun mustBlock() = scenario {
        cards()
        alice.battlefield("Plodding Bear")
        bob.battlefield("Thicketwood Elves", "Elvish Seer")
        effect(alice, Mechanic.MustBlock(arcanum.api.ObjectFilter.Any), affects = Affected.Objects(Objects.Bound("it")),
            names = mapOf("it" to "Thicketwood Elves"), duration = Duration.Indefinitely)
        toAttackers()
        alice.attacks("Plodding Bear")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks() }
        assertFailsWith<IllegalArgumentException> { bob.blocks("Elvish Seer" to "Plodding Bear") }
        bob.blocks("Thicketwood Elves" to "Plodding Bear")
    }

    // CR 508.1: only the active player declares attackers
    @Test
    fun onlyTheActivePlayerAttacks() = scenario {
        alice.battlefield("Thicketwood Elves")
        bob.battlefield("Elvish Seer")
        toAttackers()
        assertFailsWith<IllegalArgumentException> { bob.attacks("Elvish Seer") }
    }

    // CR 510.1b, 120.3a: an unblocked attacker's damage makes the defending player lose life
    @Test
    fun unblockedDamagesThePlayer() = scenario {
        cards()
        alice.battlefield("Hill Ox")
        bob.battlefield("Plodding Bear", tapped = true)
        toAttackers()
        alice.attacks("Hill Ox")
        toBlockers()
        toDamage()
        expect { bob.life shouldBe 17 }
        assertTrue(applied.any { it is Change.Damage && it.dealt.single().combat })
    }

    // CR 509.1, P5: a defending player with no possible blocker declares nothing; damage follows
    @Test
    fun noPossibleBlockersSkipsTheDeclaration() = scenario {
        cards()
        alice.battlefield("Hill Ox")
        toAttackers()
        alice.attacks("Hill Ox")
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 506.2, 508.1b, 120.3c: attacking a planeswalker: its damage removes loyalty counters
    @Test
    fun attackingAPlaneswalker() = scenario {
        cards()
        alice.battlefield("Plodding Bear")
        bob.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        toAttackers()
        alice.attacks("Plodding Bear", defender = "Renji Kagehara")
        toBlockers()
        toDamage()
        expect {
            "Renji Kagehara".counters(CounterKind.Loyalty) shouldBe 1
            bob.life shouldBe 20
        }
    }

    // CR 120.3c, 704.5i: a planeswalker with no loyalty left goes to the graveyard
    @Test
    fun planeswalkerDefeated() = scenario {
        cards()
        alice.battlefield("Hill Ox")
        bob.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        toAttackers()
        alice.attacks("Hill Ox", defender = "Renji Kagehara")
        toBlockers()
        toDamage()
        expect { bob.graveyard has "Renji Kagehara" }
    }

    // CR 510.1c–d: a blocked attacker and its blocker deal damage to each other
    @Test
    fun blockedCreaturesTrade() = scenario {
        cards()
        alice.battlefield("Plodding Bear")
        bob.battlefield("Hill Ox")
        toAttackers()
        alice.attacks("Plodding Bear")
        toBlockers()
        bob.blocks("Hill Ox" to "Plodding Bear")
        assertTrue(state.combat!!.attackers.single().blocked)
        toDamage()
        expect {
            alice.graveyard has "Plodding Bear"
            bob.battlefield has "Hill Ox"
            bob.life shouldBe 20
        }
        assertEquals(2, state.objOrThrow(card("Hill Ox")).damage)
    }

    // CR 509.1: only creatures of the defending player block
    @Test
    fun onlyTheDefenderBlocks() = scenario {
        cards()
        alice.battlefield("Plodding Bear", "Hill Ox")
        bob.battlefield("Thicketwood Elves")
        toAttackers()
        alice.attacks("Plodding Bear")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { alice.blocks("Hill Ox" to "Plodding Bear") }
    }

    // CR 702.9b: a creature with flying can't be blocked except by creatures with flying or reach
    @Test
    fun flyingNeedsFlyingOrReach() = scenario {
        alice.battlefield("Faerie Augur")
        bob.battlefield("Thicketwood Elves", "Canopy Browser", "Plumes of Eden")
        toAttackers()
        alice.attacks("Faerie Augur")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Thicketwood Elves" to "Faerie Augur") }
        bob.blocks("Canopy Browser" to "Faerie Augur")
    }

    // CR 702.9b: a flier can block another flier
    @Test
    fun flierBlocksFlier() = scenario {
        alice.battlefield("Faerie Augur")
        bob.battlefield("Plumes of Eden")
        toAttackers()
        alice.attacks("Faerie Augur")
        toBlockers()
        bob.blocks("Plumes of Eden" to "Faerie Augur")
        toDamage()
        expect { bob.graveyard has "Plumes of Eden" }
    }

    // CR 702.9b: a flier can block a creature without flying
    @Test
    fun flierBlocksGroundCreature() = scenario {
        cards()
        alice.battlefield("Plodding Bear")
        bob.battlefield("Faerie Augur")
        toAttackers()
        alice.attacks("Plodding Bear")
        toBlockers()
        bob.blocks("Faerie Augur" to "Plodding Bear")
    }

    // CR 702.111b: menace: not blocked except by two or more creatures
    @Test
    fun menaceNeedsTwoBlockers() = scenario {
        alice.battlefield("Unyielding Dead")
        bob.battlefield("Thicketwood Elves", "Elvish Seer")
        toAttackers()
        alice.attacks("Unyielding Dead")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Thicketwood Elves" to "Unyielding Dead") }
        bob.blocks("Thicketwood Elves" to "Unyielding Dead", "Elvish Seer" to "Unyielding Dead")
    }

    // CR 509.1b: "This creature can't block"
    @Test
    fun cantBlock() = scenario {
        cards()
        alice.battlefield("Plodding Bear")
        bob.battlefield("Corpse Gnawer", "Elvish Seer")
        toAttackers()
        alice.attacks("Plodding Bear")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Corpse Gnawer" to "Plodding Bear") }
    }

    // CR 509.1b: "can block only creatures with flying"
    @Test
    fun canBlockOnlyFliers() = scenario {
        cards()
        alice.battlefield("Plodding Bear", "Faerie Augur")
        bob.battlefield("Brash Pilferer")
        toAttackers()
        alice.attacks("Plodding Bear", "Faerie Augur")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Brash Pilferer" to "Plodding Bear") }
        bob.blocks("Brash Pilferer" to "Faerie Augur")
    }

    // CR 509.1b: "can't be blocked"
    @Test
    fun cantBeBlocked() = scenario {
        alice.battlefield("Shapeless Exile")
        bob.battlefield("Thicketwood Elves")
        toAttackers()
        alice.attacks("Shapeless Exile")
        allPass()
        // Nothing can block it: no declaration is awaited
        assertEquals(alice.id, state.priority.holder)
        assertFailsWith<IllegalArgumentException> { bob.blocks("Thicketwood Elves" to "Shapeless Exile") }
    }

    // CR 702.16e: protection from blue: not blocked by blue creatures
    @Test
    fun protectionFromBlueAgainstBlockers() = scenario {
        alice.battlefield("Glade Sprite Ranger")
        bob.battlefield("Faerie Augur", "Plumes of Eden")
        toAttackers()
        alice.attacks("Glade Sprite Ranger")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Faerie Augur" to "Glade Sprite Ranger") }
        bob.blocks("Plumes of Eden" to "Glade Sprite Ranger")
    }

    // CR 702.28b: shadow: blocked only by shadow, blocks only shadow
    @Test
    fun shadow() = scenario {
        cards()
        alice.battlefield("Netherbound Turncoat", "Plodding Bear")
        bob.battlefield("Thicketwood Elves")
        toAttackers()
        alice.attacks("Netherbound Turncoat", "Plodding Bear")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Thicketwood Elves" to "Netherbound Turncoat") }
        bob.blocks("Thicketwood Elves" to "Plodding Bear")
    }

    // CR 509.1a: one creature blocks one attacker unless it can block more
    @Test
    fun oneBlockerOneAttacker() = scenario {
        cards()
        alice.battlefield("Plodding Bear", "Hill Ox")
        bob.battlefield("Old Mammoth")
        toAttackers()
        alice.attacks("Plodding Bear", "Hill Ox")
        toBlockers()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Old Mammoth" to "Plodding Bear", "Old Mammoth" to "Hill Ox") }
    }

    // CR 509.1a: "can block an additional creature": it blocks two and divides its damage (CR 510.1d)
    @Test
    fun blockingTwoAttackers() = scenario {
        cards()
        alice.battlefield("Plodding Bear", "Hill Ox")
        bob.battlefield("Gloomwing Skulker")
        effect(bob, Mechanic.CanBlockAdditional(1), affects = Affected.Objects(Objects.Bound("it")),
            names = mapOf("it" to "Gloomwing Skulker"), duration = Duration.Indefinitely)
        toAttackers()
        alice.attacks("Plodding Bear", "Hill Ox")
        toBlockers()
        val blocker = card("Gloomwing Skulker")
        perform(Input.DeclareBlockers(bob.id, listOf(
            arcanum.engine.input.Block(blocker, card("Plodding Bear")),
            arcanum.engine.input.Block(blocker, card("Hill Ox", 0)),
        )))
        toDamage()
        // CR 510.1d: the blocker's 3 damage isn't enough for both: its controller divides it
        val d = assertIs<Decision.Divide>(pending)
        assertEquals(bob.id, d.player)
        bob.answers(Answer.Division(listOf(Share(target("Plodding Bear"), 2), Share(TargetRef.Object(card("Hill Ox", 0)), 1))))
        expect {
            alice.graveyard has "Plodding Bear"
            alice.battlefield has "Hill Ox"
            bob.graveyard has "Gloomwing Skulker"
        }
    }

    // CR 702.7b: first strike deals its damage before creatures without it
    @Test
    fun firstStrikeKillsFirst() = scenario {
        cards()
        alice.battlefield("Enamel Phalanxer")
        bob.battlefield("Plodding Bear")
        toAttackers()
        alice.attacks("Enamel Phalanxer")
        toBlockers()
        bob.blocks("Plodding Bear" to "Enamel Phalanxer")
        toDamage()
        // The first-strike damage step: the bear dies before it deals damage
        expect {
            bob.graveyard has "Plodding Bear"
            alice.battlefield has "Enamel Phalanxer"
        }
        assertEquals(TurnStep.CombatDamage, state.turn.step)
        allPass()
        expect { alice.battlefield has "Enamel Phalanxer" }
    }

    // CR 510.4: a first striker doesn't deal damage again in the second step
    @Test
    fun firstStrikeOnlyOnce() = scenario {
        alice.battlefield("Enamel Phalanxer")
        toAttackers()
        alice.attacks("Enamel Phalanxer")
        toBlockers()
        toDamage()
        expect { bob.life shouldBe 17 }
        // The second combat damage step: nobody else to deal damage
        allPass()
        assertEquals(TurnStep.CombatDamage, state.turn.step)
        expect { bob.life shouldBe 17 }
        allPass()
        assertEquals(TurnStep.EndOfCombat, state.turn.step)
    }

    // CR 702.4b: double strike deals first-strike and regular damage
    @Test
    fun doubleStrikeTwice() = scenario {
        cards()
        alice.battlefield("Twinblade Duelist")
        toAttackers()
        alice.attacks("Twinblade Duelist")
        toBlockers()
        toDamage()
        expect { bob.life shouldBe 18 }
        allPass()
        expect { bob.life shouldBe 16 }
    }

    // CR 702.4b, 510.1c: double strike kills the blocker first; with its blocker gone and no trample, no more damage
    @Test
    fun doubleStrikeBlockerGone() = scenario {
        cards()
        alice.battlefield("Twinblade Duelist")
        bob.battlefield("Elvish Seer")
        toAttackers()
        alice.attacks("Twinblade Duelist")
        toBlockers()
        bob.blocks("Elvish Seer" to "Twinblade Duelist")
        toDamage()
        allPass()
        expect {
            bob.graveyard has "Elvish Seer"
            bob.life shouldBe 20
        }
    }

    // CR 702.4b, 702.19b–d: double strike and trample: excess in the first step, all of it in the second
    @Test
    fun doubleStrikeAndTrample() = scenario {
        cards()
        alice.battlefield("Twinblade Stomper")
        bob.battlefield("Elvish Seer")
        toAttackers()
        alice.attacks("Twinblade Stomper")
        toBlockers()
        bob.blocks("Elvish Seer" to "Twinblade Stomper")
        toDamage()
        // CR 702.19b: the excess over lethal damage goes where its controller chooses: to the player here
        divideAsSuggested()
        expect {
            bob.graveyard has "Elvish Seer"
            bob.life shouldBe 19
        }
        allPass()
        expect { bob.life shouldBe 17 }
    }

    // CR 702.19b: trample: lethal damage to the blocker, the rest to the player
    @Test
    fun trampleExcess() = scenario {
        alice.battlefield("Mirebloat, the Undermuck")
        bob.battlefield("Gearwing Glider")
        toAttackers()
        alice.attacks("Mirebloat, the Undermuck")
        toBlockers()
        bob.blocks("Gearwing Glider" to "Mirebloat, the Undermuck")
        toDamage()
        // The excess is its controller's choice; the suggestion is lethal damage to the blocker, the rest to the player
        val d = assertIs<Decision.Divide>(pending)
        assertEquals(listOf(Share(target("Gearwing Glider"), 2), Share(TargetRef.Player(bob.id), 1)), d.suggested)
        divideAsSuggested()
        expect {
            bob.graveyard has "Gearwing Glider"
            bob.life shouldBe 19
        }
    }

    // CR 702.19b, 702.2c: with deathtouch, 1 damage is lethal: the rest tramples over
    @Test
    fun trampleWithDeathtouch() = scenario {
        cards()
        alice.battlefield("Venomhorn Brute")
        bob.battlefield("Wall of Portents")
        toAttackers()
        alice.attacks("Venomhorn Brute")
        toBlockers()
        bob.blocks("Wall of Portents" to "Venomhorn Brute")
        toDamage()
        divideAsSuggested()
        expect {
            bob.graveyard has "Wall of Portents"
            bob.life shouldBe 17
        }
    }

    // CR 702.2b, 704.5h: deathtouch destroys whatever it damages
    @Test
    fun deathtouchKillsABigBlocker() = scenario {
        alice.battlefield("Venomous Owlet")
        bob.battlefield("Canopy Browser")
        toAttackers()
        alice.attacks("Venomous Owlet")
        toBlockers()
        bob.blocks("Canopy Browser" to "Venomous Owlet")
        toDamage()
        expect { bob.graveyard has "Canopy Browser" }
    }

    // CR 702.15b: lifelink: damage dealt gains its controller that much life
    @Test
    fun lifelink() = scenario {
        alice.battlefield("Crypt Flitter")
        toAttackers()
        alice.attacks("Crypt Flitter")
        toBlockers()
        toDamage()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
        assertTrue(applied.any { it is Change.Life && Meaning.LifeGained in it.meanings })
    }

    // CR 702.15b: a blocking lifelinker gains life too
    @Test
    fun lifelinkWhenBlocking() = scenario {
        cards()
        alice.battlefield("Plodding Bear")
        bob.battlefield("Crypt Flitter")
        toAttackers()
        alice.attacks("Plodding Bear")
        toBlockers()
        bob.blocks("Crypt Flitter" to "Plodding Bear")
        toDamage()
        expect { bob.life shouldBe 21 }
    }

    // CR 510.1c: several blockers and not enough damage for all: its controller divides it
    @Test
    fun dividingAmongBlockers() = scenario {
        cards()
        alice.battlefield("Hill Ox")
        bob.battlefield("Plodding Bear", "Canopy Browser")
        toAttackers()
        alice.attacks("Hill Ox")
        toBlockers()
        bob.blocks("Plodding Bear" to "Hill Ox", "Canopy Browser" to "Hill Ox")
        toDamage()
        val d = assertIs<Decision.Divide>(pending)
        assertEquals(alice.id, d.player)
        assertEquals(3, d.amount)
        alice.answers(Answer.Division(listOf(Share(target("Plodding Bear"), 2), Share(target("Canopy Browser"), 1))))
        expect {
            bob.graveyard has "Plodding Bear"
            bob.battlefield has "Canopy Browser"
            alice.battlefield has "Hill Ox"
        }
        assertEquals(1, state.objOrThrow(card("Canopy Browser")).damage)
    }

    // CR 510.1c: all of it, only among the blockers
    @Test
    fun divisionMustUseAllDamage() = scenario {
        cards()
        alice.battlefield("Hill Ox")
        bob.battlefield("Plodding Bear", "Hill Ox")
        toAttackers()
        alice.attacks("Hill Ox")
        toBlockers()
        perform(Input.DeclareBlockers(bob.id, listOf(
            arcanum.engine.input.Block(card("Plodding Bear"), card("Hill Ox", 0)),
            arcanum.engine.input.Block(card("Hill Ox", 1), card("Hill Ox", 0)),
        )))
        toDamage()
        assertIs<Decision.Divide>(pending)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Division(listOf(Share(target("Plodding Bear"), 2)))) }
        assertFailsWith<IllegalArgumentException> { answer(Answer.Division(listOf(Share(target("Plodding Bear"), 2), Share(TargetRef.Player(bob.id), 1)))) }
    }

    // CR 510.1c: enough damage for lethal to every blocker is still divided as its controller chooses; the suggestion
    // gives each lethal damage and the rest to the last (owner's decision, 2026-11-02: as the table's "In order")
    @Test
    fun enoughForAllIsStillAChoice() = scenario {
        cards()
        alice.battlefield("Old Mammoth")
        bob.battlefield("Plodding Bear", "Elvish Seer")
        toAttackers()
        alice.attacks("Old Mammoth")
        toBlockers()
        bob.blocks("Plodding Bear" to "Old Mammoth", "Elvish Seer" to "Old Mammoth")
        toDamage()
        val d = assertIs<Decision.Divide>(pending)
        assertEquals(listOf(Share(target("Plodding Bear"), 2), Share(target("Elvish Seer"), 3)), d.suggested)
        divideAsSuggested()
        expect {
            bob.graveyard has "Plodding Bear"
            bob.graveyard has "Elvish Seer"
        }
    }

    // CR 510.1c: with enough for both, all of it may still go to one blocker
    @Test
    fun allDamageOnOneOfTwoBlockers() = scenario {
        cards()
        alice.battlefield("Old Mammoth")
        bob.battlefield("Plodding Bear", "Elvish Seer")
        toAttackers()
        alice.attacks("Old Mammoth")
        toBlockers()
        bob.blocks("Plodding Bear" to "Old Mammoth", "Elvish Seer" to "Old Mammoth")
        toDamage()
        alice.answers(Answer.Division(listOf(Share(target("Plodding Bear"), 5))))
        expect {
            bob.graveyard has "Plodding Bear"
            bob.battlefield has "Elvish Seer"
        }
    }

    // CR 702.19b: with trample, the excess may go to the blocker instead of the player
    @Test
    fun trampleExcessMayStayOnTheBlocker() = scenario {
        alice.battlefield("Mirebloat, the Undermuck")
        bob.battlefield("Gearwing Glider")
        toAttackers()
        alice.attacks("Mirebloat, the Undermuck")
        toBlockers()
        bob.blocks("Gearwing Glider" to "Mirebloat, the Undermuck")
        toDamage()
        alice.answers(Answer.Division(listOf(Share(target("Gearwing Glider"), 3))))
        expect {
            bob.graveyard has "Gearwing Glider"
            bob.life shouldBe 20
        }
    }

    // CR 702.19b: a trampler with no more than lethal damage for its one blocker assigns it all there without asking
    @Test
    fun trampleWithoutExcessIsAutomatic() = scenario {
        cards()
        alice.battlefield("Twinblade Stomper")
        bob.battlefield("Hill Ox")
        toAttackers()
        alice.attacks("Twinblade Stomper")
        toBlockers()
        bob.blocks("Hill Ox" to "Twinblade Stomper")
        toDamage()
        assertNull(pending)
        expect { bob.life shouldBe 20 }
    }

    // CR 510.1c: one blocker, no trample: no choice
    @Test
    fun oneBlockerTakesAllWithoutAsking() = scenario {
        cards()
        alice.battlefield("Old Mammoth")
        bob.battlefield("Plodding Bear")
        toAttackers()
        alice.attacks("Old Mammoth")
        toBlockers()
        bob.blocks("Plodding Bear" to "Old Mammoth")
        toDamage()
        assertNull(pending)
        expect { bob.graveyard has "Plodding Bear" }
    }

    // CR 702.19b: with trample and several blockers, the player gets damage only once each blocker has lethal
    @Test
    fun trampleDivisionNeedsLethalFirst() = scenario {
        alice.battlefield("Mirebloat, the Undermuck")
        bob.battlefield("Elvish Seer", "Canopy Browser")
        toAttackers()
        alice.attacks("Mirebloat, the Undermuck")
        toBlockers()
        bob.blocks("Elvish Seer" to "Mirebloat, the Undermuck", "Canopy Browser" to "Mirebloat, the Undermuck")
        toDamage()
        val d = assertIs<Decision.Divide>(pending)
        assertTrue(TargetRef.Player(bob.id) in d.among)
        assertFailsWith<IllegalArgumentException> {
            answer(Answer.Division(listOf(Share(target("Elvish Seer"), 1), Share(target("Canopy Browser"), 1), Share(TargetRef.Player(bob.id), 1))))
        }
        answer(Answer.Division(listOf(Share(target("Elvish Seer"), 1), Share(target("Canopy Browser"), 2), Share(TargetRef.Player(bob.id), 0))))
        expect {
            bob.graveyard has "Elvish Seer"
            bob.life shouldBe 20
        }
    }

    // CR 120.3b, 702.90b: infect: poison counters to a player
    @Test
    fun infectGivesPoison() = scenario {
        cards()
        alice.battlefield("Plague Mite")
        toAttackers()
        alice.attacks("Plague Mite")
        toBlockers()
        toDamage()
        expect { bob.life shouldBe 20 }
        assertEquals(2, bob.counters(CounterKind.Poison))
    }

    // CR 120.3d: infect: -1/-1 counters on a creature
    @Test
    fun infectOnACreature() = scenario {
        cards()
        alice.battlefield("Plague Mite")
        bob.battlefield("Hill Ox")
        toAttackers()
        alice.attacks("Plague Mite")
        toBlockers()
        bob.blocks("Hill Ox" to "Plague Mite")
        toDamage()
        expect {
            "Hill Ox".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2
            "Hill Ox".power shouldBe 1
        }
    }

    // CR 120.3e, 702.80b: wither: -1/-1 counters on a creature
    @Test
    fun witherOnACreature() = scenario {
        cards()
        alice.battlefield("Blight Hound")
        bob.battlefield("Hill Ox")
        toAttackers()
        alice.attacks("Blight Hound")
        toBlockers()
        bob.blocks("Hill Ox" to "Blight Hound")
        toDamage()
        expect { "Hill Ox".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2 }
        assertEquals(0, state.objOrThrow(card("Hill Ox")).damage)
    }

    // CR 702.164c: toxic: poison counters on combat damage to a player, as well as the damage
    @Test
    fun toxicGivesPoison() = scenario {
        cards()
        alice.battlefield("Bitter Adder")
        toAttackers()
        alice.attacks("Bitter Adder")
        toBlockers()
        toDamage()
        expect { bob.life shouldBe 19 }
        assertEquals(2, bob.counters(CounterKind.Poison))
    }

    // CR 510.1c: a blocked creature whose blocker left combat deals no damage (no trample)
    @Test
    fun blockerRemovedNoDamage() = scenario {
        cards()
        alice.battlefield("Hill Ox", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Plodding Bear")
        toAttackers()
        alice.attacks("Hill Ox")
        toBlockers()
        bob.blocks("Plodding Bear" to "Hill Ox")
        // The blocker leaves the battlefield (moved by hand here)
        move("Plodding Bear", arcanum.api.Zone.Graveyard)
        expect { bob.graveyard has "Plodding Bear" }
        allPass()
        expect { bob.life shouldBe 20 }
    }

    // CR 506.4, 511.3: combat ends with the end of combat step
    @Test
    fun combatEnds() = scenario {
        alice.battlefield("Thicketwood Elves")
        toAttackers()
        alice.attacks("Thicketwood Elves")
        advanceTo(TurnStep.PostcombatMain)
        assertNull(state.combat)
        assertTrue(applied.any { it is Change.Combat && Meaning.CombatEnded in it.meanings })
    }

    // CR 506.2, 802: three players: attack each opponent; each defending player declares blockers
    @Test
    fun threePlayerCombat() = scenario(players = 3) {
        cards()
        alice.battlefield("Plodding Bear", "Hill Ox")
        bob.battlefield("Elvish Seer")
        carol.battlefield("Thicketwood Elves")
        toAttackers()
        perform(Input.DeclareAttackers(alice.id, listOf(
            arcanum.engine.input.Attack(card("Plodding Bear"), TargetRef.Player(bob.id)),
            arcanum.engine.input.Attack(card("Hill Ox"), TargetRef.Player(carol.id)),
        )))
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
        // Bob can't block the creature attacking carol
        assertFailsWith<IllegalArgumentException> { bob.blocks("Elvish Seer" to "Hill Ox") }
        bob.blocks("Elvish Seer" to "Plodding Bear")
        assertNull(state.priority.holder)
        carol.blocks()
        assertEquals(alice.id, state.priority.holder)
        allPass()
        expect {
            carol.life shouldBe 17
            bob.life shouldBe 20
            bob.graveyard has "Elvish Seer"
        }
    }

    // CR 506.2, 102.3: a player who left the game can't be attacked
    @Test
    fun cantAttackALostPlayer() = scenario(players = 3) {
        alice.battlefield("Thicketwood Elves")
        state.updatePlayer(bob.id) { it.copy(lost = true) }
        toAttackers()
        assertFailsWith<IllegalArgumentException> { alice.attacks("Thicketwood Elves", defender = "bob") }
        alice.attacks("Thicketwood Elves", defender = "carol")
    }

    // D56: the options while attackers are awaited are declarations
    @Test
    fun optionsOfferDeclarations() = scenario {
        alice.battlefield("Thicketwood Elves")
        toAttackers()
        val options = Engine.options(state, alice.id)
        assertTrue(options.all { it is Input.DeclareAttackers })
        assertTrue(options.any { (it as Input.DeclareAttackers).attacks.isEmpty() })
        assertTrue(options.any { (it as Input.DeclareAttackers).attacks.singleOrNull()?.attacker == card("Thicketwood Elves") })
        assertTrue(Engine.options(state, bob.id).isEmpty())
    }
}
