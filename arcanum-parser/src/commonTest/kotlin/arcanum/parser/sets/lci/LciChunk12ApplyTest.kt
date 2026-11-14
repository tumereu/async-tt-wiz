package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 12: pumps and keyword grants until end of turn (attack conditions, modal team pumps, a tap-two-permanents cost, damage from a pumped creature). */
class LciChunk12ApplyTest {
    private val yourCreatures = Objects.Each(and(creature, you))
    private val isSelf = ObjectFilter.Is(Objects.Self)

    // CR 508.1m, 603.4: the condition is checked as it triggers and as it resolves
    @Test
    fun brazenBlademaster() = card(
        name = "Brass Blademaster",
        cost = "{2}{R}",
        type = "Creature — Orc Pirate",
        pt = "2/3",
        text = "Whenever this creature attacks while you control two or more artifacts, it gets +2/+1 until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(steps = listOf(pump(Objects.Self, 2, 1))),
            triggersOnlyIf = Condition.Exists(and(artifact, you), atLeast = 2),
        ),
    )

    // CR 508.1m, 509.1: attacks or blocks
    @Test
    fun burningSunCavalry() = card(
        name = "Sunlit Cavalry",
        cost = "{1}{R}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = "Whenever this creature attacks or blocks while you control a Dinosaur, this creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(EventPattern.Attack(isSelf), EventPattern.Block(isSelf, attacker = ObjectFilter.Any))),
            instructions = Instructions(steps = listOf(pump(Objects.Self, 1, 1))),
            triggersOnlyIf = Condition.Exists(and(sub("Dinosaur"), you), atLeast = 1),
        ),
    )

    // CR 702.110a (menace), 603.1
    @Test
    fun dinotomaton() = card(
        name = "Dinomaton",
        cost = "{3}{R}",
        type = "Artifact Creature — Dinosaur Gnome",
        pt = "4/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, target creature you control gains menace until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, gains(target("creature"), Keyword.Menace), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 700.2: one mode is chosen as the spell is cast
    @Test
    fun familyReunion() = card(
        name = "Kindred Reunion",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures you control get +1/+1 until end of turn.
            • Creatures you control gain hexproof until end of turn. (They can't be the targets of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(pump(yourCreatures, 1, 1)),
            mode(gains(yourCreatures, Keyword.Hexproof)),
        ))),
    )

    // CR 118.3, 701.26a: tapping untapped permanents you control as a cost (not the ones an opponent controls)
    @Test
    fun goldfuryStrider() = card(
        name = "Ironfury Strider",
        cost = "{4}{R}",
        type = "Artifact Creature — Golem",
        pt = "3/5",
        text = """
            Trample
            Tap two untapped artifacts and/or creatures you control: Target creature gets +2/+0 until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), n(2)))), pump(target("creature"), 2, 0),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 613.4c
    @Test
    fun hermiticNautilus() = card(
        name = "Hermit Nautilus",
        cost = "{1}{U}",
        type = "Artifact Creature — Nautilus",
        pt = "1/4",
        text = """
            Vigilance
            {1}{U}: This creature gets +3/-3 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(manaCost("{1}{U}"), pump(Objects.Self, 3, -3)),
    )

    // CR 115.1: "another target creature"
    @Test
    fun hotfootGnome() = card(
        name = "Footsore Gnome",
        cost = "{2}{R}",
        type = "Artifact Creature — Gnome",
        pt = "3/1",
        text = """
            Haste
            {T}: Another target creature gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(tap), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", and(another, creature)))),
    )

    // CR 608.2c, 120.3: the pumped creature is the source of the damage, and its power is read as the damage is dealt
    @Test
    fun huatlisFinalStrike() = card(
        name = "Hatli's Final Strike",
        cost = "{2}{G}",
        type = "Instant",
        text = "Target creature you control gets +1/+0 until end of turn. It deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 1, 0),
            doing(Effect.DealDamage(
                to = Recipients(objects = target("creature an opponent controls")),
                amount = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                source = target("creature"),
            )),
            targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", and(creature, opponents))),
        ),
    )

    // CR 506.2, 508.1: the target must be an attacking creature
    @Test
    fun malametBrawler() = card(
        name = "Malabar Brawler",
        cost = "{1}{G}",
        type = "Creature — Cat Warrior",
        pt = "2/2",
        text = "Whenever this creature attacks, target attacking creature gains trample until end of turn.",
    ) parsesTo abilities(
        triggered(attacksSelf, gains(target("creature"), Keyword.Trample), targets = listOf(slot("creature", and(attacking, creature)))),
    )

    @Test
    fun malametWarScribe() = card(
        name = "Malabar War Scribe",
        cost = "{3}{W}{W}",
        type = "Creature — Cat Warrior",
        pt = "4/3",
        text = "When this creature enters, creatures you control get +2/+1 until end of turn.",
    ) parsesTo abilities(triggered(entersSelf, pump(yourCreatures, 2, 1)))

    // CR 507.1: beginning of combat on your turn
    @Test
    fun mightOfTheAncestors() = card(
        name = "Might of the Forebears",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "At the beginning of combat on your turn, target creature you control gets +2/+0 and gains vigilance until end of turn.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, untilEndOfTurn(target("creature"), pt(2, 0), has(Keyword.Vigilance)),
            targets = listOf(slot("creature", creatureYouControl))),
    )
}
