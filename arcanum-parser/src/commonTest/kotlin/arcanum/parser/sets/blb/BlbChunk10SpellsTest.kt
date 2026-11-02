package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Bloomburrow chunk 10: instants, sorceries and simple activated tricks. Pumps with keywords and untapping, "up to one
 * other target", targets with statuses ("attacking, blocking, or tapped"), cost reductions on a condition or on what
 * the spell targets (CR 601.2f), "instead" for an "unless" counterspell, an owner's choice of top or bottom, two
 * targets controlled by different players, a creature dealing damage equal to its power, token copies for X.
 */
class BlbChunk10SpellsTest {
    private fun spell(targets: List<TargetSlot>, vararg steps: Step) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
    private fun pumpAndGain(objects: Objects, power: Int, toughness: Int, vararg keywords: Keyword) = Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.ModifyPowerToughness(one(power), one(toughness)), Mechanic.GainAbilities(keywords.toList())),
        Affected.Objects(objects), Duration.EndOfTurn))
    private val notYours = ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))
    private val token = ObjectFilter.Category(ObjectCategory.Token)

    @Test
    fun boldkinPair() = card(
        name = "Boldkin Pair",
        cost = "{W}",
        type = "Creature — Rabbit Mouse",
        pt = "1/1",
        text = "{1}, {T}: Target creature gets +1/+1 until end of turn. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{1}"), CostPart.TapSelf)),
            instructions = Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(Objects.Target("creature"), 1, 1))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    // CR 115.3: "up to one other target creature" can't be the first target
    @Test
    fun maribelsGrit() = card(
        name = "Maribel's Grit",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. Up to one other target creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", creature), TargetSlot("other creature", TargetKind.Objects(creature), min = one(0), max = one(),
                distinctFrom = listOf("creature"))),
            pump(Objects.Target("creature"), 2, 2),
            pump(Objects.Target("other creature"), 1, 1),
        ),
    )

    @Test
    fun bunnyRally() = card(
        name = "Bunny Rally",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = "Creatures you control get +2/+1 until end of turn. If you control a Rabbit, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)",
    ) parsesTo abilities(
        spell(
            emptyList(),
            pump(Objects.Each(creatureYouControl), 2, 1),
            Step.If(Condition.Exists(ObjectFilter.And(listOf(sub("Rabbit"), you)), atLeast = 1), then = listOf(Step.Do(Effect.Scry(Players.You, one(2))))),
        ),
    )

    @Test
    fun wardOffDisaster() = card(
        name = "Ward Off Disaster",
        cost = "{1}{W}",
        type = "Instant",
        text = "Destroy target creature with power or toughness 4 or greater.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Or(listOf(ObjectFilter.Power(Comparison.AtLeast, one(4)),
                ObjectFilter.Toughness(Comparison.AtLeast, one(4)))))))),
            Step.Do(Effect.Destroy(Objects.Target("creature"))),
        ),
    )

    // CR 506.4, 509.1: attacking and blocking creatures; "you gain 3 life if you control a Bat" as it resolves
    @Test
    fun echoStrike() = card(
        name = "Echo Strike",
        cost = "{1}{W}",
        type = "Instant",
        text = "Echo Strike deals 4 damage to target attacking, blocking, or tapped creature. You gain 3 life if you control a Bat.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.HasStatus(Status.Attacking),
                ObjectFilter.HasStatus(Status.Blocking), ObjectFilter.HasStatus(Status.Tapped))), creature)))),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), one(4), source = Objects.Self)),
            Step.If(Condition.Exists(ObjectFilter.And(listOf(sub("Bat"), you)), atLeast = 1), then = listOf(Step.Do(Effect.GainLife(Players.You, one(3))))),
        ),
    )

    @Test
    fun burrowElder() = card(
        name = "Burrow Elder",
        cost = "{1}{W}",
        type = "Creature — Rabbit Cleric",
        pt = "2/2",
        text = "{3}{W}: Creatures you control get +1/+1 until end of turn.",
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(mana("{3}{W}"))), steps(pump(Objects.Each(creatureYouControl), 1, 1)),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)),
    )

    @Test
    fun disastrousSurge() = card(
        name = "Disastrous Surge",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = "Return up to two target creatures to their owners' hands. Draw two cards, then discard a card.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creatures", creature, min = 0, max = 2)),
            Step.Do(Effect.Move(Objects.Target("creatures"), Destination(Zone.Hand))),
            Step.Do(Effect.Draw(Players.You, one(2))),
            Step.Do(Effect.Discard(Players.You, one(), filter = ObjectFilter.Any)),
        ),
    )

    // CR 118.12a: with a Bird, the controller pays {4} rather than {2} to keep the spell
    @Test
    fun glitteringRefusal() = card(
        name = "Glittering Refusal",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {2}. If you control a Bird, counter that spell unless its controller pays {4} instead.",
    ) parsesTo abilities(
        run {
            val controller = Players.ControllerOf(Objects.Target("spell"))
            fun unless(cost: String) = listOf(
                Step.Pay(Cost(listOf(mana(cost))), payer = controller, bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(Objects.Target("spell"))))),
            )
            spell(
                listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
                Step.If(Condition.Exists(ObjectFilter.And(listOf(sub("Bird"), you)), atLeast = 1), then = unless("{4}"), otherwise = unless("{2}")),
            )
        },
    )

    // CR 601.2f: the reduction applies when the spell's target is such a creature; the owner chooses top or bottom
    @Test
    fun grimDowngust() = card(
        name = "Grim Downgust",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets an attacking or tapped creature.
            Target creature's owner puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, one()))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.ObjectMatches(Objects.Target("creature"),
                ObjectFilter.Or(listOf(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Tapped)))),
        ),
        spell(
            listOf(slot("creature", creature)),
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(Objects.Target("creature"))),
            Step.If(
                Condition.Chose("position", "top"),
                then = listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Library, libraryPosition = 0)))),
                otherwise = listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Library, libraryPosition = -1)))),
            ),
        ),
    )

    @Test
    fun pearlOfInsight() = card(
        name = "Pearl of Insight",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            This spell costs {1} less to cast if you control an Otter.
            Draw two cards.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, one()))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Exists(ObjectFilter.And(listOf(sub("Otter"), you)), atLeast = 1),
        ),
        spell(emptyList(), Step.Do(Effect.Draw(Players.You, one(2)))),
    )

    // CR 601.2c: the second target's controller differs from the first's
    @Test
    fun fleeTogether() = card(
        name = "Flee Together",
        cost = "{1}{U}",
        type = "Instant",
        text = "Choose two target creatures controlled by different players. Return those creatures to their owners' hands.",
    ) parsesTo abilities(
        spell(
            listOf(
                slot("creature", creature),
                TargetSlot("other creature", TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(
                    PlayerFilter.Is(Players.ControllerOf(Objects.Target("creature")))))))), min = one(), max = one(), distinctFrom = listOf("creature")),
            ),
            Step.Do(Effect.Move(Objects.Union(listOf(Objects.Target("creature"), Objects.Target("other creature"))), Destination(Zone.Hand))),
        ),
    )

    @Test
    fun braceUp() = card(
        name = "Brace Up",
        cost = "{U}",
        type = "Instant",
        text = "Target creature you control gets +1/+1 and gains hexproof until end of turn. Untap it. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", creatureYouControl)),
            pumpAndGain(Objects.Target("creature"), 1, 1, Keyword.Hexproof),
            Step.Do(Effect.Untap(Objects.Target("creature"))),
        ),
    )

    // CR 702.41a: affinity for a creature type
    @Test
    fun slateScales() = card(
        name = "Slate Scales",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Affinity for Lizards (This spell costs {1} less to cast for each Lizard you control.)
            Target creature gets +2/+0 and gains lifelink and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Lizard"), "Lizards"),
        spell(listOf(slot("creature", creature)), pumpAndGain(Objects.Target("creature"), 2, 0, Keyword.Lifelink, Keyword.Indestructible)),
    )

    // CR 120.3: two damage events of one spell
    @Test
    fun arcConduction() = card(
        name = "Arc Conduction",
        cost = "{4}{R}",
        type = "Instant",
        text = "Arc Conduction deals 6 damage to target creature and 2 damage to up to one target creature token.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", creature), slot("token", ObjectFilter.And(listOf(creature, token)), min = 0)),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), one(6), source = Objects.Self)),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("token")), one(2), source = Objects.Self)),
        ),
    )

    @Test
    fun strengthOfTheSmall() = card(
        name = "Strength of the Small",
        cost = "{R}",
        type = "Instant",
        text = """
            Target creature gains trample until end of turn. It also gets +1/+0 until end of turn if you control a Mouse.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", creature)),
            gains(Objects.Target("creature"), Keyword.Trample),
            Step.If(Condition.Exists(ObjectFilter.And(listOf(sub("Mouse"), you)), atLeast = 1), then = listOf(pump(Objects.Target("creature"), 1, 0))),
            Step.Do(Effect.Draw(Players.You, one())),
        ),
    )

    @Test
    fun cheekyPush() = card(
        name = "Cheeky Push",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Cheeky Push deals 1 damage to any target.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            listOf(TargetSlot("any", TargetKind.Either(ObjectFilter.Or(listOf(CardType.Creature, CardType.Planeswalker, CardType.Battle).map { ObjectFilter.Type(it) }),
                PlayerFilter.Any), min = one(), max = one())),
            Step.Do(Effect.DealDamage(Recipients(target = "any"), one(), source = Objects.Self)),
            Step.Do(Effect.Draw(Players.You, one())),
        ),
    )

    // CR 608.2c: the pumped creature deals the damage, with its power then
    @Test
    fun frenziedChomp() = card(
        name = "Frenzied Chomp",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature you control gets +1/+0 until end of turn. Then it deals damage equal to its power to target creature you don't control.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", creatureYouControl), slot("creature you don't control", notYours)),
            pump(Objects.Target("creature"), 1, 0),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature you don't control")),
                Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single), source = Objects.Target("creature"))),
        ),
    )

    @Test
    fun haulOffTheRubbish() = card(
        name = "Haul Off the Rubbish",
        cost = "{1}{R}",
        type = "Instant",
        text = "Haul Off the Rubbish deals 3 damage to target creature or planeswalker. If you control a Raccoon, you may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        spell(
            listOf(slot("permanent", ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker))))),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("permanent")), one(3), source = Objects.Self)),
            Step.If(Condition.Exists(ObjectFilter.And(listOf(sub("Raccoon"), you)), atLeast = 1), then = listOf(
                Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = one())))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
            )),
        ),
    )

    // CR 107.3: X tokens; CR 611.2c: the tokens you control as it resolves gain indestructible
    @Test
    fun forTheSharedWeal() = card(
        name = "For the Shared Weal",
        cost = "{X}{X}{G}",
        type = "Sorcery",
        text = "Create X tokens that are copies of target token you control. Then tokens you control gain indestructible until your next turn. You gain 1 life for each token you control.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("token", ObjectFilter.And(listOf(token, you)))),
            steps = listOf(
                Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Target("token")), count = Value.Announced("X"), controller = Players.You)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Indestructible))),
                    Affected.Objects(Objects.Each(ObjectFilter.And(listOf(token, you)))), Duration.UntilYourNextTurn)),
                Step.Do(Effect.GainLife(Players.You, Value.Count(Objects.Each(ObjectFilter.And(listOf(token, you)))))),
            ),
            announced = listOf("X"),
        )),
    )

    @Test
    fun tallStride() = card(
        name = "Tall Stride",
        cost = "{G}",
        type = "Instant",
        text = "Target creature gets +1/+3 and gains reach until end of turn. Untap it.",
    ) parsesTo abilities(
        spell(listOf(slot("creature", creature)), pumpAndGain(Objects.Target("creature"), 1, 3, Keyword.Reach), Step.Do(Effect.Untap(Objects.Target("creature")))),
    )

    @Test
    fun overguard() = card(
        name = "Overguard",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control gets +3/+3 and gains trample, hexproof, and indestructible until end of turn.",
    ) parsesTo abilities(
        spell(listOf(slot("creature", creatureYouControl)), pumpAndGain(Objects.Target("creature"), 3, 3, Keyword.Trample, Keyword.Hexproof, Keyword.Indestructible)),
    )

    @Test
    fun tadpoleSmack() = card(
        name = "Tadpole Smack",
        cost = "{3}{G}",
        type = "Instant",
        text = """
            Affinity for Frogs (This spell costs {1} less to cast for each Frog you control.)
            Target creature you control deals damage equal to twice its power to target creature you don't control.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Frog"), "Frogs"),
        spell(
            listOf(slot("creature", creatureYouControl), slot("creature you don't control", notYours)),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature you don't control")),
                Value.Times(Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single), 2), source = Objects.Target("creature"))),
        ),
    )

    @Test
    fun feralBite() = card(
        name = "Feral Bite",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Target creature you control deals damage equal to its power to target creature you don't control.",
    ) parsesTo abilities(
        spell(
            listOf(slot("creature", creatureYouControl), slot("creature you don't control", notYours)),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature you don't control")),
                Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single), source = Objects.Target("creature"))),
        ),
    )
}
