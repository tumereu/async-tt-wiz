package arcanum.engine.sets.mkm

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tdm.Plain
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MKM fixes: a triggered ability's targets keep the limits they put on each other, as a spell's do (CR 603.3d, 601.2c). */
class MkmTriggerTargetLimitsScenariosTest {
    private val kestrel = Cards.invent("Ashsift Kestrel", "{2}{W}", "Creature — Bird Detective",
        "When this creature enters, exile up to two target cards from a single graveyard.", "2/2")
    private val accuser = Cards.invent("Twinfold Accuser", "{3}{R}", "Creature — Human Detective",
        "When this creature enters, choose two target creatures controlled by the same opponent. That player chooses one of those creatures. " +
            "This creature deals 5 damage to that creature, and the other can't block this turn.", "3/3")
    private val recaller = Cards.invent("Kinward Recaller", "{2}{G}", "Creature — Elf Druid",
        "When this creature enters, return two target creature cards that share a creature type from your graveyard to your hand.", "2/2")
    private val rememberer = Cards.invent("Manyfaced Rememberer", "{4}{B}", "Creature — Zombie",
        "When this creature enters, return up to six target creature cards with different names from your graveyard to the battlefield.", "2/2")
    private val archivist = Cards.invent("Typecount Archivist", "{4}{U}", "Creature — Human Wizard",
        "When this creature enters, if you cast it, exile up to one target nonland card of each card type from your graveyard. Copy those cards. " +
            "You may cast any number of spells with total mana value 6 or less from among the copies without paying their mana costs.", "3/3")
    private val scavenger = Cards.invent("Bonetithe Scavenger", "{2}{B}", "Creature — Rat Rogue",
        "When this creature enters, you may sacrifice another creature. When you do, exile up to two target cards from a single graveyard.", "2/2")
    private val lens = Cards.invent("Mirrorstep Lens", "{3}", "Artifact",
        "{1}, {T}: Copy target activated or triggered ability you control. You may choose new targets for the copy.")
    private val cub = Cards.invent("Marsh Cub", "{G}", "Creature — Bear", "", "1/1")

    private val graveCards = TargetSlot("cards", TargetKind.Objects(ObjectFilter.InZone(Zone.Graveyard)), Value.Fixed(0), Value.Fixed(2), sameOwner = true)

    /** "At the beginning of the next end step, exile up to two target cards from a single graveyard." (written by hand: the
     * parser doesn't read a delayed trigger with its own targets yet) */
    private val gravesweep = Cards.define("Patient Gravesweep", CardDefinition(Layout.Normal, listOf(FaceDefinition("Patient Gravesweep",
        ManaCost.parse("{1}{B}"), types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(
            Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(targets = listOf(graveCards), steps = listOf(Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Exile)))))))))))))))

    /** Resolves the Stack until a decision is asked (a step begun by the harness has nobody holding priority: the Stack
     * resolves on its own). */
    private fun Scenario.resolveStack() {
        var guard = 40
        while (guard-- > 0 && pending == null && state.stack.isNotEmpty()) if (state.priority.holder == null) resolve() else allPass()
    }

    /** "You may choose new targets for target activated or triggered ability." (written by hand: CR 115.7d's wording on a spell
     * the parser doesn't read yet) */
    private val redirect = Cards.define("Whisperturn Redirect", CardDefinition(Layout.Normal, listOf(FaceDefinition("Whisperturn Redirect",
        ManaCost.parse("{U}"), types = listOf(CardType.Instant), abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(TargetSlot("ability", TargetKind.Objects(ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility)), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.ChangeTargets(Objects.Target("ability"), arcanum.api.Players.You, optional = true))))))))))

    private fun Scenario.targets(vararg names: String) = Answer.Targets(names.map { target(it) })

    // CR 603.3d, 601.2c, 115.1: "from a single graveyard" on an enters trigger: one card of Bob's and one of Carol's is refused
    @Test
    fun singleGraveyardTriggerRefusesTwoGraveyards() = scenario(players = 3) {
        alice.hand(kestrel)
        bob.graveyard(Plain.bear, Plain.ogre)
        carol.graveyard(Plain.giant)
        alice.castsWithoutCosts(kestrel)
        resolveStack()
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.bear, Plain.giant)) }
        alice.answers(targets(Plain.bear, Plain.ogre))
        resolveStack()
        expect {
            bob.exile has Plain.bear
            bob.exile has Plain.ogre
            carol.graveyard has Plain.giant
        }
    }

    // CR 603.3d, 601.2c: "controlled by the same opponent": Bob's creature and Carol's together are refused
    @Test
    fun sameOpponentTriggerRefusesTwoControllers() = scenario(players = 3) {
        alice.hand(accuser)
        bob.battlefield(Plain.bear, Plain.elf)
        carol.battlefield(Plain.ogre)
        alice.castsWithoutCosts(accuser)
        resolveStack()
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.bear, Plain.ogre)) }
        alice.answers(targets(Plain.bear, Plain.elf))
    }

    // CR 603.3d: no two creatures share an opponent: there's no legal choice of targets, so the trigger is removed from the Stack
    @Test
    fun sameOpponentTriggerWithoutAPairIsRemoved() = scenario(players = 3) {
        alice.hand(accuser)
        bob.battlefield(Plain.bear)
        carol.battlefield(Plain.ogre)
        alice.castsWithoutCosts(accuser)
        resolveStack()
        expect {
            Plain.bear.zone shouldBe Zone.Battlefield
            Plain.ogre.zone shouldBe Zone.Battlefield
        }
        assertTrue(state.stack.isEmpty() && pending !is Decision.Targets, "pending $pending, stack ${state.stack}")
    }

    // CR 603.3d, 601.2c: "two target creature cards that share a creature type": an Ogre and a Bear are refused
    @Test
    fun sharingTriggerRefusesCardsWithoutASharedType() = scenario {
        alice.hand(recaller)
        alice.graveyard(Plain.ogre, Plain.bear, cub)
        alice.castsWithoutCosts(recaller)
        resolveStack()
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.ogre, Plain.bear)) }
        alice.answers(targets(Plain.bear, cub))
        resolveStack()
        expect {
            alice.hand has Plain.bear
            alice.hand has cub
            alice.graveyard has Plain.ogre
        }
    }

    // CR 603.3d, 601.2c: the answers offered for the trigger's targets keep the limit: a pair that shares a type, where the first
    // two cards don't
    @Test
    fun sharingTriggerOffersAPairThatShares() = scenario {
        alice.hand(recaller)
        alice.graveyard(Plain.ogre, Plain.bear, cub)
        alice.castsWithoutCosts(recaller)
        resolveStack()
        decision<Decision.Targets>()
        val offered = Engine.options(state, alice.id).filterIsInstance<Input.Answer>().map { it.answer }
        assertEquals(listOf(targets(Plain.bear, cub).chosen.toSet()), offered.map { (it as Answer.Targets).chosen.toSet() })
    }

    // CR 603.3d: no two creature cards share a type: no legal choice, the trigger is removed
    @Test
    fun sharingTriggerWithoutAPairIsRemoved() = scenario {
        alice.hand(recaller)
        alice.graveyard(Plain.ogre, Plain.bear)
        alice.castsWithoutCosts(recaller)
        resolveStack()
        expect {
            alice.graveyard has Plain.ogre
            alice.graveyard has Plain.bear
        }
        assertTrue(pending !is Decision.Targets, "pending $pending")
    }

    // CR 603.3d, 601.2c: "with different names": two cards of one name are refused
    @Test
    fun differentNamesTriggerRefusesTwoOfOneName() = scenario {
        alice.hand(rememberer)
        alice.graveyard(Plain.bear, Plain.bear, Plain.ogre)
        alice.castsWithoutCosts(rememberer)
        resolveStack()
        decision<Decision.Targets>()
        val bears = decision<Decision.Targets>().options.filter { (it as? TargetRef.Object)?.id?.let { id -> state.obj(id)?.let { o -> nameOf(o) } } == Plain.bear }
        assertEquals(2, bears.size)
        assertFailsWith<IllegalArgumentException> { alice.answers(Answer.Targets(bears)) }
        alice.answers(Answer.Targets(listOf(bears.first(), target(Plain.ogre))))
    }

    // CR 603.3d, 601.2c: "up to one target … of each card type": two cards that are only creatures are refused
    @Test
    fun eachCardTypeTriggerRefusesTwoOfOneType() = scenario {
        alice.hand(archivist)
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.graveyard(Plain.bear, Plain.ogre, Plain.study)
        alice.casts(archivist)
        resolveStack()
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.bear, Plain.ogre)) }
        alice.answers(targets(Plain.bear, Plain.study))
    }

    // CR 603.7c, 603.3d, 601.2c: a delayed trigger's "from a single graveyard" is kept too
    @Test
    fun delayedTriggerKeepsSingleGraveyard() = scenario(players = 3) {
        alice.hand(gravesweep)
        bob.graveyard(Plain.bear)
        carol.graveyard(Plain.giant)
        alice.castsWithoutCosts(gravesweep)
        resolveStack()
        beginStep(TurnStep.End)
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.bear, Plain.giant)) }
        alice.answers(targets(Plain.giant))
        resolveStack()
        expect {
            carol.exile has Plain.giant
            bob.graveyard has Plain.bear
        }
    }

    // CR 603.12, 603.3d, 601.2c: a reflexive trigger ("When you do, …") chooses its targets the same way
    @Test
    fun reflexiveTriggerKeepsSingleGraveyard() = scenario(players = 3) {
        alice.hand(scavenger)
        alice.battlefield(Plain.elf)
        bob.graveyard(Plain.bear)
        carol.graveyard(Plain.giant)
        alice.castsWithoutCosts(scavenger)
        resolveStack()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(Plain.elf)
        resolveStack()
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.bear, Plain.giant)) }
        alice.answers(targets(Plain.bear))
        resolveStack()
        expect {
            alice.graveyard has Plain.elf
            bob.exile has Plain.bear
            carol.graveyard has Plain.giant
        }
    }

    // CR 707.10c, 601.2c: a copy of the trigger whose new targets are chosen keeps "from a single graveyard"
    @Test
    fun copiedTriggersNewTargetsKeepSingleGraveyard() = scenario(players = 3) {
        alice.hand(kestrel)
        alice.battlefield(lens, "Plains", "Plains", "Plains", "Plains")
        bob.graveyard(Plain.bear, Plain.ogre)
        carol.graveyard(Plain.giant, Plain.elf)
        alice.casts(kestrel, paying = listOf("Plains", "Plains#1", "Plains#2"))
        allPass()
        alice.answers(targets(Plain.bear, Plain.ogre))
        alice.activates(lens)
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(TargetRef.Object(state.stack.first()))))
        (pending as? Decision.ManaPayment)?.let { alice.answers(Answer.ManaPayment(it.suggestion)) }
        allPass()
        alice.answers(Answer.YesNo(true))
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.giant, Plain.bear)) }
        alice.answers(targets(Plain.giant, Plain.elf))
        resolveStack()
        expect {
            bob.exile has Plain.bear
            bob.exile has Plain.ogre
            carol.exile has Plain.giant
            carol.exile has Plain.elf
        }
    }

    // CR 115.7d, 601.2c: new targets chosen for the trigger by an effect keep "from a single graveyard"
    @Test
    fun changedTriggerTargetsKeepSingleGraveyard() = scenario(players = 3) {
        alice.hand(kestrel, redirect)
        alice.battlefield("Plains", "Plains", "Plains", "Island")
        bob.graveyard(Plain.bear, Plain.ogre)
        carol.graveyard(Plain.giant, Plain.elf)
        alice.casts(kestrel, paying = listOf("Plains", "Plains#1", "Plains#2"))
        allPass()
        alice.answers(targets(Plain.bear, Plain.ogre))
        alice.casts(redirect, paying = listOf("Island"))
        alice.answers(Answer.Targets(listOf(decision<Decision.Targets>().options.single())))
        (pending as? Decision.ManaPayment)?.let { alice.answers(Answer.ManaPayment(it.suggestion)) }
        allPass()
        alice.answers(Answer.YesNo(true))
        decision<Decision.Targets>()
        assertFailsWith<IllegalArgumentException> { alice.answers(targets(Plain.giant, Plain.bear)) }
        alice.answers(targets(Plain.giant, Plain.elf))
        resolveStack()
        expect {
            bob.graveyard has Plain.bear
            carol.exile has Plain.giant
            carol.exile has Plain.elf
        }
    }
}
