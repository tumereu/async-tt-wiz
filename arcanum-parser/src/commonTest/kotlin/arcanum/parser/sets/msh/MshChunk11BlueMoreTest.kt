package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Cost
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 11: more blue cards. Hexproof for creatures you've put counters on this turn; a player or permanent
 * becoming the target of your ability (CR 115.1); copies of each nontoken creature you control (CR 707.9b); blue mana symbols of the
 * cast spell (CR 107.4) as a count of tokens; exile until the Aura leaves and the enchanted creature copying the exiled card (CR
 * 610.3, layer 1); a delayed return at the next end step (CR 603.7); one artifact becoming a copy of another (CR 707.9b); a draw at the
 * enchanted creature's controller's upkeep; "discard two cards unless you discard an artifact card" (CR 118.12a); losing all
 * abilities for as long as it remains (CR 611.2b).
 */
class MshChunk11BlueMoreTest {
    @Test
    fun youngTrickster() = card(
        name = "Young Trickster",
        cost = "{U}",
        type = "Legendary Creature — God Hero Villain",
        text = """
            Each creature you control that you've put one or more +1/+1 counters on this turn has hexproof.
            Whenever you draw your second card each turn, put a +1/+1 counter on Young Trickster.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), affects = Objects.Each(and(creatureYouControl,
            ObjectFilter.ThisTurn(EventPattern.CountersPut(ObjectFilter.Any, p1p1, causedBy = byYou), role = EventRole.Object)))),
        triggered(secondCardEachTurn, counters(Objects.Self)),
    )

    // CR 603.2i: once each turn
    @Test
    fun jesterGodOfMischief() = card(
        name = "Jester, God of Mischief",
        cost = "{1}{U}",
        type = "Legendary Creature — God Sorcerer Villain",
        text = "Whenever a player or permanent becomes the target of an ability you control, draw a card. This ability triggers only once each turn.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(on(EventPattern.BecomesTarget(permanentFilter, by = and(ObjectFilter.Category(ObjectCategory.StackAbility), you), players = PlayerFilter.Any)),
            draw(1), perTurn = 1),
    )

    @Test
    fun doctorElasticRubberGenius() = card(
        name = "Doctor Elastic, Rubber Genius",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Scientist Hero",
        text = """
            Reach
            Whenever one or more tokens you control enter, you may draw a card.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(arcanum.api.Trigger.On(EventPattern.ZoneChange(and(token, you), to = Zone.Battlefield), oneOrMore = true),
            Step.May(listOf(draw(1)), player = Players.You)),
    )

    // CR 707.9b: copies except they aren't legendary
    @Test
    fun multiversalBreach() = card(
        name = "Multiversal Breach",
        cost = "{5}{U}{U}",
        type = "Sorcery",
        text = "For each nontoken creature you control, create a token that's a copy of that creature, except it isn't legendary.",
    ) parsesTo abilities(
        spell(Step.ForEach(arcanum.api.Each.Objects(Objects.Each(and(not(token), creature, you))), "creature", listOf(
            create(TokenSpec.CopyOf(Objects.Bound("creature"), except = listOf(arcanum.api.CopyException.Types(removeSupertypes = listOf(arcanum.api.Supertype.Legendary)))))))),
    )

    // CR 604.3: a characteristic-defining power; CR 107.4: blue mana symbols, hybrid ones included, counted on the cast spell
    @Test
    fun nerithTheTideKing() = card(
        name = "Nerith the Tide-King",
        cost = "{1}{U}{U}",
        type = "Legendary Creature — Mutant Merfolk Villain",
        text = """
            Flying
            Nerith's power is equal to the number of Merfolk you control.
            Whenever you cast a noncreature spell with one or more blue mana symbols in its mana cost, create that many 1/1 blue Merfolk creature tokens.
        """,
        pt = "*/4",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(count(and(sub("Merfolk"), you)), null), characteristicDefining = true),
        triggered(youCast(and(noncreatureSpell, spellFilter, ObjectFilter.Where(Condition.Compare(Value.Of(NumericProperty.ManaSymbols(Color.Blue), Objects.Tested, Combine.Single),
            arcanum.api.Comparison.AtLeast, n(1))))),
            create(creatureToken("Merfolk", listOf(Color.Blue), 1, 1), Value.Of(NumericProperty.ManaSymbols(Color.Blue), Objects.EventObject(EventRole.Object), Combine.Single))),
    )

    @Test
    fun shrinkingParticles() = card(
        name = "Shrinking Particles",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Target creature gains vigilance until end of turn and can't be blocked this turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), has(Keyword.Vigilance), Mechanic.CantBeBlocked), draw(1), targets = listOf(slot("creature", creature))),
    )

    // CR 610.3 and layer 1: the enchanted creature copies the exiled card until the Aura leaves
    @Test
    fun covertInvasion() = card(
        name = "Covert Invasion",
        cost = "{1}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When this Aura enters, exile up to one target creature other than enchanted creature until this Aura leaves the battlefield. Enchanted creature becomes a copy of that creature until this Aura leaves the battlefield.
            Enchanted creature has ward {2}.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creatureYouControl)),
        triggered(entersSelf,
            Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("exiled"))), arcanum.api.Affected.Objects(Objects.AttachedTo(Objects.Self)),
                Duration.UntilLeaves(Objects.Self))),
            targets = listOf(slot("creature", and(creature, not(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))), min = 0))),
        static(has(Keyword.Ward(manaCost("{2}"))), affects = Objects.AttachedTo(Objects.Self)),
    )

    @Test
    fun bureauDeploymentDrone() = card(
        name = "Bureau Deployment Drone",
        cost = "{2}{U}",
        type = "Artifact Creature — Robot",
        text = """
            Flying
            When this creature enters, create a 1/1 white Soldier creature token.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(soldier)),
    )

    // CR 603.7: returned at the beginning of the next end step
    @Test
    fun bureauFlyingCar() = card(
        name = "Bureau Flying Car",
        cost = "{2}{U}",
        type = "Artifact — Vehicle",
        text = """
            Flash
            Flying
            When this Vehicle enters, exile up to one target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
            Crew 1
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, move(target("creature"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), postBind = "returned"))))),
            targets = listOf(slot("creature", creatureYouControl, min = 0))),
        Keyword.Crew(1),
    )

    // CR 707.9b: one artifact becoming a copy of another, except it isn't legendary
    @Test
    fun princessImaniRoyalInventor() = card(
        name = "Princess Imani, Royal Inventor",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Artificer Hero",
        text = """
            Artifact spells you cast cost {1} less to cast.
            {1}, {T}: Target artifact you control becomes a copy of a second target artifact you control until end of turn, except it isn't legendary. Activate only as a sorcery.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(artifact, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        activated(cost(mana("{1}"), tap), untilEndOfTurn(target("artifact"), Mechanic.CopyOf(target("second artifact"),
                except = listOf(arcanum.api.CopyException.Types(removeSupertypes = listOf(arcanum.api.Supertype.Legendary))))),
            targets = listOf(slot("artifact", and(artifact, you)), slot("second artifact", and(artifact, you)).copy(distinctFrom = listOf("artifact"))),
            rules = asSorcery),
    )

    // the upkeep of the enchanted creature's controller: that player is the active player
    @Test
    fun superIntellect() = card(
        name = "Super Intellect",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            At the beginning of the upkeep of enchanted creature's controller, that player draws a card.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        triggered(on(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.Is(Players.ControllerOf(Objects.AttachedTo(Objects.Self))))), draw(1, Players.Active)),
    )

    @Test
    fun prototypeSuperSuit() = card(
        name = "Prototype Super Suit",
        cost = "{1}{U}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. Untap that creature.
            Equipped creature gets +1/+2.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), untapIt(target("creature")),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 2), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 118.12a: "unless you discard an artifact card"
    @Test
    fun hungerForLearning() = card(
        name = "Hunger for Learning",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw three cards. Then discard two cards unless you discard an artifact card.",
    ) parsesTo abilities(
        spell(draw(3),
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(artifact, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(discard(2)))),
    )

    // CR 603.7: the exiled card returns at the next end step
    @Test
    fun hexenRisingMagician() = card(
        name = "Hexen, Rising Magician",
        cost = "{4}{U}",
        type = "Legendary Creature — Mutant Warlock Hero",
        text = """
            Flying
            Whenever you cast a noncreature spell, exile another target nonland, nontoken permanent. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreatureSpell), move(target("permanent"), Zone.Exile, postBind = "exiled"), returnAtNextEndStep,
            targets = listOf(slot("permanent", and(another, nonland, not(token), permanentFilter)))),
    )

    // CR 611.2b: for as long as it remains on the battlefield
    @Test
    fun theWondrousHornet() = card(
        name = "The Wondrous Hornet",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Hero",
        text = """
            Flash
            Flying
            Wasp's Sting — When The Wondrous Hornet enters, tap up to one target creature. It loses all abilities for as long as The Wondrous Hornet remains on the battlefield.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, tapIt(target("creature")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.LoseAbilities()), arcanum.api.Affected.Objects(target("creature")), Duration.UntilLeaves(Objects.Self))),
            targets = listOf(slot("creature", creature, min = 0)), abilityWord = "Wasp's Sting"),
    )

    private val returnAtNextEndStep = Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
        move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))))
}
