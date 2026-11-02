package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 05: Classes (CR 716). Each class level bar "[cost]: Level N" is a keyword ability with the
 * abilities printed in its section ([Keyword.ClassLevel]): activating it makes the Class level N, only from level N-1
 * and as a sorcery, and from level N on the Class has those abilities (CR 716.2a). The top section's abilities are the
 * Class's own (CR 716.3); "When this Class becomes level N" is a trigger of a level's section. Also here: tokens named
 * and with abilities, "equipped creatures you control", counters doubled for whoever puts them, damage plus 2.
 */
class BlbChunk05ClassesTest {
    private fun level(cost: String, level: Int, vararg abilities: Ability) = Keyword.ClassLevel(manaCost(cost), level, abilities.toList())
    private fun becomesLevel(level: Int) = Trigger.On(EventPattern.BecomesLevel(ObjectFilter.Is(Objects.Self), level))
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val permanent = ObjectFilter.Category(ObjectCategory.Permanent)
    private val token = ObjectFilter.Category(ObjectCategory.Token)
    private val otter = creatureToken("Otter", listOf(Color.Blue, Color.Red), 1, 1, listOf(Keyword.Prowess))
    private val opponentsUpkeep = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.Opponent))

    // CR 716.2a: level 2's trigger works from level 2 on; level 3's "becomes level 3" triggers as it gets there
    @Test
    fun masonsKnack() = card(
        name = "Mason's Knack",
        cost = "{1}{W}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, create a 0/4 white Wall creature token with defender.
            {W}: Level 2
            Whenever one or more noncreature, nonland permanents you control enter, put a +1/+1 counter on target creature you control.
            {4}{W}: Level 3
            When this Class becomes level 3, return target noncreature, nonland permanent card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(creatureToken("Wall", listOf(Color.White), 0, 4, listOf(Keyword.Defender)), count = one(),
            controller = Players.You))),
        level("{W}", 2, Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Not(land), permanent, you)),
                to = Zone.Battlefield), oneOrMore = true),
            instructions = Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(counters(Objects.Target("creature")))),
        )),
        level("{4}{W}", 3, Ability.Triggered(
            trigger = becomesLevel(3),
            instructions = Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Not(land),
                    ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
                        CardType.Planeswalker).map { ObjectFilter.Type(it) })) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield)))),
            ),
        )),
    )

    // CR 603.2h: "triggers only once each turn"; CR 707.2: a token copy
    @Test
    fun stewardsKnack() = card(
        name = "Steward's Knack",
        cost = "{2}{W}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever one or more tokens you control enter, draw a card. This ability triggers only once each turn.
            {W}: Level 2
            When this Class becomes level 2, create a token that's a copy of target token you control.
            {3}{W}: Level 3
            Creature tokens you control get +2/+2.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(token, you)), to = Zone.Battlefield), oneOrMore = true),
            instructions = steps(Step.Do(Effect.Draw(Players.You, one()))),
            perTurn = 1,
        ),
        level("{W}", 2, Ability.Triggered(
            trigger = becomesLevel(2),
            instructions = Instructions(
                targets = listOf(slot("token", ObjectFilter.And(listOf(token, you)))),
                steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Target("token")), count = one(), controller = Players.You))),
            ),
        )),
        level("{3}{W}", 3, Ability.Static(
            listOf(Mechanic.ModifyPowerToughness(one(2), one(2))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, token, you)))),
        )),
    )

    // CR 506.3, 509.1b: "can't be blocked this turn"; CR 400.7: the creature returned is a new object
    @Test
    fun rumormongersKnack() = card(
        name = "Rumormonger's Knack",
        cost = "{1}{U}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever a creature you control enters, surveil 1.
            {1}{U}: Level 2
            Whenever you attack, target attacking creature with power 3 or less can't be blocked this turn.
            {3}{U}: Level 3
            Whenever a creature you control deals combat damage to a player, you may exile it, then return it to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creatureYouControl, to = Zone.Battlefield)),
            instructions = steps(Step.Do(Effect.Surveil(Players.You, one()))),
        ),
        level("{1}{U}", 2, Ability.Triggered(
            trigger = youAttack,
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(arcanum.api.Status.Attacking), creature,
                    ObjectFilter.Power(Comparison.AtMost, one(3)))))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeBlocked), Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn))),
            ),
        )),
        level("{3}{U}", 3, Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)),
            instructions = steps(Step.May(listOf(
                Step.Do(Effect.Move(Objects.EventObject(EventRole.Source), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                    postBind = "returned")),
            ), player = Players.You)),
        )),
    )

    @Test
    fun squallchasersKnack() = card(
        name = "Squallchaser's Knack",
        cost = "{U}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, create a 1/1 blue and red Otter creature token with prowess.
            {3}{U}: Level 2
            When this Class becomes level 2, return target instant or sorcery card from your graveyard to your hand.
            {5}{U}: Level 3
            Whenever you cast an instant or sorcery spell, create a 1/1 blue and red Otter creature token with prowess.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(otter, count = one(), controller = Players.You))),
        level("{3}{U}", 2, Ability.Triggered(
            trigger = becomesLevel(2),
            instructions = Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant),
                    ObjectFilter.Type(CardType.Sorcery)))) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand)))),
            ),
        )),
        level("{5}{U}", 3, Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.CreateToken(otter, count = one(), controller = Players.You))),
        )),
    )

    // CR 118.12a, 101.4: each opponent may discard a nonland card; those who don't discard two cards
    @Test
    fun brigandsKnack() = card(
        name = "Brigand's Knack",
        cost = "{1}{B}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, each opponent discards two cards unless they discard a nonland card.
            {B}: Level 2
            At the beginning of each opponent's upkeep, if that player has one or fewer cards in hand, they lose 2 life.
            {3}{B}: Level 3
            At the beginning of your draw step, draw an additional card for each opponent who has one or fewer cards in hand.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.ForEach(
            Each.Players(Players.Each(PlayerFilter.Opponent)),
            bind = "opponent",
            together = true,
            steps = listOf(
                Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Not(land), count = one())))), payer = Players.Bound("opponent"),
                    bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Discard(Players.Bound("opponent"), one(2),
                    filter = ObjectFilter.Any)))),
            ),
        )),
        level("{B}", 2, Ability.Triggered(
            trigger = opponentsUpkeep,
            instructions = steps(Step.Do(Effect.LoseLife(Players.EventPlayer, one(2)))),
            interveningIf = Condition.Compare(Value.HandSize(Players.EventPlayer), Comparison.AtMost, one()),
        )),
        level("{3}{B}", 3, Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Draw, PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.Draw(Players.You, Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.AtMost, one()))))))))),
        )),
    )

    // CR 701.21a, 118.12, 122.1h
    @Test
    fun gleanersKnack() = card(
        name = "Gleaner's Knack",
        cost = "{B}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever one or more creatures you control die, create a Food token. This ability triggers only once each turn.
            {1}{B}: Level 2
            Whenever you sacrifice a permanent, target player mills two cards.
            {2}{B}: Level 3
            At the beginning of your end step, you may sacrifice three other nonland permanents. If you do, return a creature card from your graveyard to the battlefield with a finality counter on it.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            instructions = steps(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Food"), count = one(), controller = Players.You))),
            perTurn = 1,
        ),
        level("{1}{B}", 2, Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(you)),
            instructions = Instructions(targets = listOf(targetPlayer), steps = listOf(Step.Do(Effect.Mill(Players.Target("player"), one(2))))),
        )),
        level("{2}{B}", 3, Ability.Triggered(
            trigger = yourEndStep,
            instructions = steps(
                Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Not(land), permanent)), count = one(3))))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(
                    Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature) + yourGraveyard), min = one(), max = one()), bind = "chosen",
                        chooser = Players.You),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, one()))))),
                )),
            ),
        )),
    )

    // CR 601.2f, 118.7: a reduction for your noncreature spells; CR 614.1a: the damage changed, plus 2
    @Test
    fun paintersKnack() = card(
        name = "Painter's Knack",
        cost = "{1}{R}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever you cast a noncreature spell, you may discard a card. If you do, draw a card.
            {2}{R}: Level 2
            Noncreature spells you cast cost {1} less to cast.
            {2}{R}: Level 3
            If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals that much damage plus 2 instead.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), PlayerFilter.You)),
            instructions = steps(
                Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = one())))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
            ),
        ),
        level("{2}{R}", 2, Ability.Static(
            listOf(Mechanic.CostChange(
                what = ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Spell), you)),
                change = CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, times = one()),
            )),
            affects = Affected.Objects(Objects.Self),
        )),
        level("{2}{R}", 3, Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.Damage(you, RecipientFilter(objects = ObjectFilter.And(listOf(permanent, opponents)), players = PlayerFilter.Opponent),
                    combat = false),
                modify = listOf(Modification.AddAmount(one(2))),
            )),
            affects = Affected.Objects(Objects.Self),
        )),
    )

    // CR 111.4: a named token with abilities; CR 701.3: attaching; CR 613.1f: granted keywords during your turn
    @Test
    fun armorersKnack() = card(
        name = "Armorer's Knack",
        cost = "{R}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, create a colorless Equipment artifact token named Sword with "Equipped creature gets +1/+1" and equip {2}.
            {2}{R}: Level 2
            At the beginning of combat on your turn, attach target Equipment you control to up to one target creature you control.
            {3}{R}: Level 3
            During your turn, equipped creatures you control have double strike and haste.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(
            TokenSpec.Defined(FaceDefinition(
                name = "Sword",
                colorIndicator = emptyList(),
                types = listOf(CardType.Artifact),
                subtypes = listOf(Subtype("Equipment")),
                abilities = listOf(
                    Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(), one())), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
                    Keyword.Equip(manaCost("{2}")),
                ),
            )),
            count = one(),
            controller = Players.You,
        ))),
        level("{2}{R}", 2, Ability.Triggered(
            trigger = yourCombat,
            instructions = Instructions(
                targets = listOf(slot("Equipment", ObjectFilter.And(listOf(sub("Equipment"), you))), slot("creature", creatureYouControl, min = 0)),
                steps = listOf(Step.Do(Effect.Attach(Objects.Target("Equipment"), Recipients(objects = Objects.Target("creature"))))),
            ),
        )),
        level("{3}{R}", 3, Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Keyword.DoubleStrike, Keyword.Haste))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Attached(attachments = sub("Equipment")), creature, you)))),
            condition = Condition.YourTurn,
        )),
    )

    // CR 120.3: the first target deals damage equal to its power to the second (not a fight)
    @Test
    fun trackersKnack() = card(
        name = "Tracker's Knack",
        cost = "{1}{G}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, target creature you control deals damage equal to its power to target creature you don't control.
            {1}{G}: Level 2
            Whenever you attack, target attacking creature gets +1/+0 and gains trample until end of turn.
            {3}{G}: Level 3
            At the beginning of your end step, if you control a creature with power 4 or greater, draw a card.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.DealDamage(
                Recipients(objects = Objects.Target("creature you don't control")),
                Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single),
                source = Objects.Target("creature"),
            )),
            targets = listOf(
                slot("creature", creatureYouControl),
                slot("creature you don't control", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
            ),
        ),
        level("{1}{G}", 2, Ability.Triggered(
            trigger = youAttack,
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(arcanum.api.Status.Attacking), creature)))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.ModifyPowerToughness(one(1), one(0)), Mechanic.GainAbilities(listOf(Keyword.Trample))),
                    Affected.Objects(Objects.Target("creature")),
                    Duration.EndOfTurn,
                ))),
            ),
        )),
        level("{3}{G}", 3, Ability.Triggered(
            trigger = yourEndStep,
            instructions = steps(Step.Do(Effect.Draw(Players.You, one()))),
            interveningIf = Condition.Exists(ObjectFilter.And(listOf(creature, ObjectFilter.Power(Comparison.AtLeast, one(4)), you)), atLeast = 1),
        )),
    )

    // CR 702.21a: granted ward; CR 614.1a: counters you put on permanents or players doubled
    @Test
    fun tavernkeepersKnack() = card(
        name = "Tavernkeeper's Knack",
        cost = "{1}{G}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.
            {G}: Level 2
            Permanents you control with counters on them have ward {1}.
            {3}{G}: Level 3
            If you would put one or more counters on a permanent or player, put twice that many of each of those kinds of counters on that permanent or player instead.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = yourCombat,
            instructions = Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(counters(Objects.Target("creature")))),
        ),
        level("{G}", 2, Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Keyword.Ward(manaCost("{1}"))))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(permanent, you, ObjectFilter.HasCounter(null, atLeast = 1))))),
        )),
        level("{3}{G}", 3, Ability.Static(
            listOf(
                Mechanic.Replacement(EventPattern.CountersPut(permanent, causedBy = you), modify = listOf(Modification.MultiplyAmount(2))),
                Mechanic.Replacement(EventPattern.PlayerCountersPut(PlayerFilter.Any, causedBy = you), modify = listOf(Modification.MultiplyAmount(2))),
            ),
            affects = Affected.Objects(Objects.Self),
        )),
    )
}
