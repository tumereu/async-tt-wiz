package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Doubled
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 08: Vehicles with crew, typecycling and cycling, a battle-only attack trigger, and Auras (CR 702.122, 702.29, 303.4). */
class MomChunk08CrewCyclingAndAurasTest {
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private fun enchant(filter: ObjectFilter) = Keyword.Enchant(TargetKind.Objects(filter))
    private val crewReminder = "(Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)"
    private val cyclingReminder = "({2}, Discard this card: Draw a card.)"
    private fun typecyclingReminder(type: String, a: String = "a") =
        "({2}, Discard this card: Search your library for $a $type card, reveal it, put it into your hand, then shuffle.)"
    private val thopter = creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying), types = listOf(CardType.Artifact, CardType.Creature))
    private val anyColor = Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)

    // CR 701.53a; CR 702.122a
    @Test
    fun ravagingDreadhull() = card(
        name = "Ravaging Dreadhull",
        cost = "{2}{R}",
        type = "Artifact — Vehicle",
        text = """
            Haste
            When this Vehicle enters, incubate 2. ${incubateReminder("two")}
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/1",
    ) parsesTo abilities(Keyword.Haste, triggered(entersSelf, incubate(2)), Keyword.Crew(2))

    // CR 602.5b: the condition is checked as it is activated; uncrewed, this Vehicle is no creature (CR 301.7b), so it can't. The mana is any
    // one of the five colours (ManaKind.Choose with no colour list, CR 106.1)
    @Test
    fun gearwheelSprinter() = card(
        name = "Gearwheel Sprinter",
        cost = "{2}",
        type = "Artifact — Vehicle",
        text = """
            Vigilance
            {T}: Add one mana of any color. Activate only if this permanent is a creature.
            Crew 1 $crewReminder
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Activated(cost(tap), Instructions(steps = listOf(doing(anyColor))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.ObjectMatches(Objects.Self, creature))),
        Keyword.Crew(1),
    )

    // CR 310.12a: a Siege's protector; CR 712.2: its back face; CR 604.3: the power is a characteristic-defining ability
    @Test
    fun invasionOfGhirapurSpires() = cardFaces(Layout.Transform,
        OracleCard("Invasion of Ghirapur Spires", "{U}{R}", "Battle — Siege",
            "$siegeReminder\nWhen this Siege enters, create a 1/1 colorless Thopter artifact creature token with flying.", defense = "4") to abilities(
            triggered(entersSelf, create(thopter)),
        ),
        card(
            name = "Skywing, Gilded-Fin Flagship",
            cost = null,
            type = "Legendary Artifact — Vehicle",
            text = """
                Flying
                Skywing's power is equal to the number of artifacts you control.
                Crew 1 $crewReminder
            """,
            pt = "*/4",
            colorIndicator = "UR",
        ) to abilities(
            Keyword.Flying,
            static(Mechanic.SetPowerToughness(count(and(artifact, you)), null), characteristicDefining = true),
            Keyword.Crew(1),
        ),
    )

    // CR 701.53a; CR 702.29e: swampcycling
    @Test
    fun syringeCrocodile() = card(
        name = "Syringe Crocodile",
        cost = "{4}{B}{B}",
        type = "Creature — Phyrexian Crocodile",
        text = """
            When this creature dies, incubate 3. ${incubateReminder("three")}
            Swampcycling {2} ${typecyclingReminder("Swamp")}
        """,
        pt = "5/5",
    ) parsesTo abilities(triggered(diesSelf, incubate(3)), Keyword.Typecycling(sub("Swamp"), "Swampcycling", manaCost("{2}")))

    // CR 303.4a; CR 702.21a: ward on the enchanted creature; CR 702.29a
    @Test
    fun prismCarapace() = card(
        name = "Prism Carapace",
        cost = "{3}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +3/+3 and has ward {2}.
            Cycling {2} $cyclingReminder
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(3, 3), has(Keyword.Ward(manaCost("{2}"))), affects = enchanted),
        Keyword.Cycling(manaCost("{2}")),
    )

    // CR 702.29e
    @Test
    fun forgeHostCharger() = card(
        name = "Forge Host Charger",
        cost = "{5}{R}",
        type = "Creature — Phyrexian Giant",
        text = """
            Haste
            Mountaincycling {2} ${typecyclingReminder("Mountain")}
        """,
        pt = "5/5",
    ) parsesTo abilities(Keyword.Haste, Keyword.Typecycling(sub("Mountain"), "Mountaincycling", manaCost("{2}")))

    // CR 614.1a: the replacement adds one to each placement on an artifact or creature you control; CR 602.5d
    @Test
    fun ozmolithTheBrokenSpire() = card(
        name = "Ozmolith, the Broken Spire",
        cost = "{1}{G}",
        type = "Legendary Artifact",
        text = """
            If one or more +1/+1 counters would be put on an artifact or creature you control, that many plus one +1/+1 counters are put on it instead.
            {1}{G}, {T}: Put a +1/+1 counter on target artifact or creature you control. Activate only as a sorcery.
            Cycling {2} $cyclingReminder
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CountersPut(and(or(artifact, creature), you), p1p1), modify = listOf(Modification.AddAmount(n(1))))),
        activated(cost(mana("{1}{G}"), tap), counters(target("permanent")), targets = listOf(slot("permanent", and(or(artifact, creature), you))), rules = asSorcery),
        Keyword.Cycling(manaCost("{2}")),
    )

    // CR 118.12, 603.5: an optional cost paid as it resolves, two other untapped creatures you control (a tap cost chooses only among the
    // payer's own untapped permanents, so the filter names no controller); CR 509.1b
    @Test
    fun tidewaterTerror() = card(
        name = "Tidewater Terror",
        cost = "{4}{U}{U}",
        type = "Creature — Octopus",
        text = """
            Whenever this creature attacks, you may tap two other untapped creatures you control. If you do, this creature can't be blocked this turn.
            Islandcycling {2} ${typecyclingReminder("Island", "an")}
        """,
        pt = "5/6",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(and(another, creature), count = n(2)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)))),
        Keyword.Typecycling(sub("Island"), "Islandcycling", manaCost("{2}")),
    )

    // CR 702.29e
    @Test
    fun timberholdAncient() = card(
        name = "Timberhold Ancient",
        cost = "{4}{G}{G}",
        type = "Creature — Treefolk",
        text = """
            Reach, trample
            Forestcycling {2} ${typecyclingReminder("Forest")}
        """,
        pt = "6/5",
    ) parsesTo abilities(Keyword.Reach, Keyword.Trample, Keyword.Typecycling(sub("Forest"), "Forestcycling", manaCost("{2}")))

    // CR 701.53a; CR 701.27a: transform the target; CR 701.10e: double its +1/+1 counters, the same object after it transformed (CR 711.8)
    @Test
    fun elvishVatwarden() = card(
        name = "Elvish Vatwarden",
        cost = "{1}{B}{G}",
        type = "Creature — Phyrexian Elf",
        text = """
            When this creature enters, incubate 2. ${incubateReminder("two")}
            {5}: Transform target Incubator token you control. Double the number of +1/+1 counters on it.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, incubate(2)),
        activated(manaCost("{5}"), doing(Effect.Transform(target("token"))),
            doing(Effect.Action(KeywordAction.Double(Doubled.Counters(target("token"), p1p1)))),
            targets = listOf(slot("token", incubatorToken))),
    )

    // CR 506.2, 508.1b: only an attack on a battle; CR 701.10b: +X/+0 where X is its power as the ability resolves
    @Test
    fun battleTrainedSlasher() = card(
        name = "Battle-Trained Slasher",
        cost = "{3}{R}",
        type = "Creature — Wolverine Dinosaur",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever this creature attacks a battle, double its power until end of turn.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self), target = RecipientFilter(objects = battle))),
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(power(Objects.Self), n(0)))),
    )

    // CR 303.4a; CR 605.1b: the added mana is a triggered mana ability's, for the land's controller (CR 106.12a)
    @Test
    fun blightedSprouting() = card(
        name = "Blighted Sprouting",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, incubate 2. ${incubateReminder("two")}
            Whenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.
        """,
    ) parsesTo abilities(
        enchant(land),
        triggered(entersSelf, incubate(2)),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(enchanted), forMana = true)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
    )

    // CR 701.17a: each player mills; CR 613.4c: -X/-0, X counted continuously from its controller's graveyard (CR 611.3a)
    @Test
    fun unsettlingConversion() {
        val minus = Value.Times(Value.Count(Objects.Each(and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.ControllerOf(enchanted)))))), -1)
        card(
            name = "Unsettling Conversion",
            cost = "{1}{U}",
            type = "Enchantment — Aura",
            text = """
                Flash
                Enchant creature
                When this Aura enters, each player mills two cards.
                Enchanted creature gets -X/-0, where X is the number of cards in its controller's graveyard.
            """,
        ) parsesTo abilities(
            Keyword.Flash,
            enchant(creature),
            triggered(entersSelf, mill(2, Players.Each(PlayerFilter.Any))),
            Ability.Static(listOf(Mechanic.ModifyPowerToughness(minus, n(0))), affects = Affected.Objects(enchanted)),
        )
    }

    // CR 508.1c, 509.1b, 602.5; CR 605.1a: its mana abilities can still be activated
    @Test
    fun realmsplittersGrasp() = card(
        name = "Realmsplitter's Grasp",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            Enchanted permanent can't attack or block, and its activated abilities can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = false), affects = enchanted),
    )

    // CR 613.4b: base power and toughness; CR 613.1f: defender is kept, every other ability lost (one effect, layer 6)
    @Test
    fun stillnessField() = card(
        name = "Stillness Field",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has base power and toughness 0/2, has defender, and loses all other abilities.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(Mechanic.SetPowerToughness(n(0), n(2)), Mechanic.LoseAbilities(), has(Keyword.Defender), affects = enchanted),
    )

    // CR 603.10a: the Aura looks back at the creature leaving; CR 701.25a: surveil
    @Test
    fun botchedConversion() = card(
        name = "Botched Conversion",
        cost = "{4}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -4/-4.
            When enchanted creature dies, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(-4, -4), affects = enchanted),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Surveil(Players.You, n(2)))),
    )
}
