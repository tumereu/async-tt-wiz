package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** WOE chunk 03: Food (CR 111.10b) and the cards that make, sacrifice and count Foods. */
class WoeChunk03FoodTest {
    private val aFood = sub("Food")
    private val human = creatureToken("Human", listOf(Color.White), 1, 1)
    private val creatureSlot = slot("creature", creature)
    private val entering = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val foods = count(and(aFood, you))

    /** "you may sacrifice a Food. If you do, …" is a payment on resolution (CR 118.12). */
    private fun mayPay(cost: CostPart, vararg then: Step): List<Step> =
        listOf(Step.Pay(cost(cost), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = then.toList()))

    // CR 118.12: "If you don't" after an optional sacrifice; CR 715 (Adventure)
    @Test
    fun ravenousCandymaw() = cardFaces(Layout.Adventure,
        card(name = "Ravenous Candymaw", cost = "{2}{B}{B}", type = "Creature — Horror", pt = "6/6", text = """
            Menace, trample
            At the beginning of your upkeep, you may sacrifice an artifact, enchantment, or token. If you don't, tap this creature.
        """.trimIndent()) to abilities(
            Keyword.Menace,
            Keyword.Trample,
            triggered(yourUpkeep,
                Step.Pay(cost(sacrificeA(or(artifact, enchantment, token))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(tapIt(Objects.Self)))),
        ),
        card(name = "Set the Table", cost = "{1}{W}", type = "Instant — Adventure",
            text = "Create a 1/1 white Human creature token and a Food token. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(create(human), createFood)),
    )

    // CR 107.3a ("where X is"), CR 119.3 (life gained this turn); CR 115.4 (up to one target)
    @Test
    fun syrupAlchemist() = cardFaces(Layout.Adventure,
        card(name = "Syrup Alchemist", cost = "{2}{B}", type = "Creature — Human Warlock", pt = "3/2", text = """
            Lifelink
            When this creature enters, up to one target creature gets -X/-X until end of turn, where X is the amount of life you gained this turn.
        """.trimIndent()) to abilities(
            Keyword.Lifelink,
            triggered(entersSelf,
                untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(
                    Value.Times(Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount), -1),
                    Value.Times(Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount), -1))),
                targets = listOf(slot("creature", creature, min = 0))),
        ),
        card(name = "Offer Sweets", cost = "{B}", type = "Instant — Adventure",
            text = "Create a Food token. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(createFood)),
    )

    // CR 107.3m (X of the permanent), CR 107.1a (rounding up); CR 122.1 (counters as it enters)
    @Test
    fun theGanderMatron() = card(
        name = "The Gander Matron",
        cost = "{X}{G}{U}",
        type = "Legendary Creature — Bird Hydra",
        pt = "2/2",
        text = """
            Flying
            The Gander Matron enters with X +1/+1 counters on it.
            When The Gander Matron enters, create half X Food tokens, rounded up.
            Whenever The Gander Matron attacks, you may sacrifice a Food. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.Replacement(
            event = entering,
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(of = Objects.Self))),
        )), affects = Affected.Objects(Objects.Self)),
        triggered(entersSelf, create(food, Value.Half(Value.CastX(of = Objects.Self), roundUp = true))),
        triggered(attacksSelf, *mayPay(sacrificeA(aFood), draw(1)).toTypedArray()),
    )

    // CR 111.10j (Wicked Role); CR 714.2b
    @Test
    fun theHagsPride() = card(
        name = "The Hag's Pride",
        cost = "{1}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Destroy target creature an opponent controls with mana value 2 or less.
            II — Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            III — Create a Wicked Role token attached to target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has "When this creature dies, each opponent loses 1 life.")
        """,
    ) parsesTo abilities(
        chapter(1, destroy(target("creature")), targets = listOf(slot("creature", and(creature, opponents, manaValue(Comparison.AtMost, 2))))),
        chapter(2, createFood),
        chapter(3, createRole("Wicked", target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 701.21 (sacrifice event)
    @Test
    fun recklessPastrycook() = card(
        name = "Reckless Pastrycook",
        cost = "{2}{B}",
        type = "Creature — Human Peasant",
        pt = "2/3",
        text = """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you sacrifice a Food, create a 1/1 black Rat creature token with "This token can't block."
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        triggered(on(EventPattern.Sacrifice(and(aFood, you))),
            create(creatureToken("Rat", listOf(Color.Black), 1, 1, listOf(static(Mechanic.CantBlock(ObjectFilter.Any)))))),
    )

    // CR 608.2c: "If it's your turn" is checked as the spell resolves
    @Test
    fun stokeTheKettle() = card(
        name = "Stoke the Kettle",
        cost = "{2}{B}",
        type = "Instant",
        text = "Destroy target creature with mana value 3 or less. If it's your turn, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
    ) parsesTo abilities(
        spell(destroy(target("creature")), ifThen(Condition.YourTurn, createFood),
            targets = listOf(slot("creature", and(creature, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 610.3 (until it leaves the battlefield)
    @Test
    fun sugarSlumber() = card(
        name = "Sugar Slumber",
        cost = "{3}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target creature an opponent controls until this enchantment leaves the battlefield. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)), createFood,
            targets = listOf(slot("creature", and(creature, opponents)))),
    )

    @Test
    fun biscuitStalker() = cardFaces(Layout.Adventure,
        card(name = "Biscuit Stalker", cost = "{4}{G}", type = "Creature — Giant", pt = "5/5",
            text = "When this creature enters, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
        ) to abilities(triggered(entersSelf, createFood)),
        card(name = "Tiny Bite", cost = "{2}{B}", type = "Instant — Adventure",
            text = "Target creature gets -2/-2 until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(pump(target("creature"), -2, -2), targets = listOf(creatureSlot))),
    )

    // CR 602.5d (sorcery timing); CR 118.12
    @Test
    fun hazelCandylandReaper() = card(
        name = "Hazel, Candyland Reaper",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Human Warrior",
        pt = "3/3",
        text = """
            When Hazel enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {G}, Sacrifice a Food: Put a +1/+1 counter on target creature. Activate only as a sorcery.
            {1}{B}, Sacrifice a Food: You draw a card and you lose 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        activated(cost(mana("{G}"), sacrificeA(aFood)), counters(target("creature")), targets = listOf(creatureSlot), rules = asSorcery),
        activated(cost(mana("{1}{B}"), sacrificeA(aFood)), draw(1), loseLife(1)),
    )

    // CR 702.20? no: CR 602.5b (once each turn)
    @Test
    fun emptyForager() = cardFaces(Layout.Adventure,
        card(name = "Empty Forager", cost = "{2}{G}", type = "Creature — Wolf", pt = "3/2",
            text = "{1}, Sacrifice a Food: This creature gets +2/+2 until end of turn. Activate only once each turn.",
        ) to abilities(activated(cost(mana("{1}"), sacrificeA(aFood)), pump(Objects.Self, 2, 2),
            rules = ActivationRules(perTurn = 1, timing = Timing.Any, activatedBy = PlayerFilter.You))),
        card(name = "Pantry Heist", cost = "{G}", type = "Sorcery — Adventure",
            text = "Create a Food token. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(createFood)),
    )

    // CR 506.5 (attacks alone)
    @Test
    fun boldMushroomrooter() = cardFaces(Layout.Adventure,
        card(name = "Bold Mushroomrooter", cost = "{1}{G}", type = "Creature — Boar", pt = "3/1",
            text = "Whenever this creature attacks alone, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
        ) to abilities(triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self), alone = true)), createFood)),
        card(name = "Mud Frenzy", cost = "{1}{G}", type = "Instant — Adventure",
            text = "Target creature gets +2/+2 until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(pump(target("creature"), 2, 2), targets = listOf(creatureSlot))),
    )

    // CR 700.4 (dies)
    @Test
    fun peppermintHorror() = card(
        name = "Peppermint Horror",
        cost = "{1}{B}",
        type = "Creature — Horror",
        pt = "3/1",
        text = "When this creature dies, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
    ) parsesTo abilities(triggered(diesSelf, createFood))

    // CR 113.3d (granted ability), CR 605.1a (mana ability); CR 613.1f
    @Test
    fun duskOfThePantrysWrath() = card(
        name = "Dusk of the Pantry's Wrath",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Foods you control have "{T}: Add {G}."
            {5}{G}{G}, Sacrifice this enchantment: Creatures you control get +X/+X until end of turn, where X is the number of Foods you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        static(Mechanic.GainAbilities(listOf(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))))),
            affects = Objects.Each(and(aFood, you))),
        activated(cost(mana("{5}{G}{G}"), sacrificeSelf),
            untilEndOfTurn(Objects.Each(creatureYouControl), Mechanic.ModifyPowerToughness(foods, foods)), rules = asSorcery),
    )

    // CR 700.4, 603.4 (intervening if)
    @Test
    fun ancientGloomwing() = card(
        name = "Ancient Gloomwing",
        cost = "{4}{B}",
        type = "Legendary Creature — Rat Faerie",
        pt = "3/4",
        text = """
            Flying
            At the beginning of each end step, if a creature died this turn, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {2}{B}, Sacrifice another creature or artifact: This creature gets +2/+2 until end of turn.
        """.replace("This creature gets", "Ancient Gloomwing gets"),
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any), createFood,
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)),
        activated(cost(mana("{2}{B}"), sacrificeA(and(another, or(creature, artifact)))), pump(Objects.Self, 2, 2)),
    )

    // CR 118.12; CR 508.1 (attacking creatures)
    @Test
    fun suppliesPeddler() = card(
        name = "Supplies Peddler",
        cost = "{2}{G}{G}",
        type = "Creature — Beast Peasant",
        pt = "3/3",
        text = """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever this creature attacks, you may sacrifice a Food. If you do, attacking creatures get +1/+1 and gain trample until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        triggered(attacksSelf, *mayPay(sacrificeA(aFood),
            untilEndOfTurn(Objects.Each(and(attacking, creature)), pt(1, 1), Mechanic.GainAbilities(listOf(Keyword.Trample)))).toTypedArray()),
    )

    // CR 305.7? no: CR 614.1c? the land enters tapped (CR 614.12); CR 205.1b; CR 508.1
    @Test
    fun fidgetyHut() = card(
        name = "Fidgety Hut",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B} or {G}.
            {2}{B}{G}: This land becomes a 4/4 black and green Horror creature until end of turn. It's still a land.
            Whenever this land attacks, create a Food token and exile up to one target card from a graveyard.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(event = entering, modify = listOf(Modification.EntersTapped))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Green)))), Players.You))),
        activated(cost(mana("{2}{B}{G}")), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Horror"))), replaceSubtypes = true),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Green))),
            Mechanic.SetPowerToughness(n(4), n(4)))),
        triggered(attacksSelf, createFood, move(target("card"), Zone.Exile, postBind = "exiled"),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
    )

    // CR 700.2 (choose two)
    @Test
    fun comeBackFromTheWoods() = card(
        name = "Come Back from the Woods",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Choose two —
            • Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            • Create a 1/1 white Human creature token.
            • Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle))),
            Mode(Instructions(steps = listOf(create(human)))),
            Mode(Instructions(steps = listOf(createFood))),
        ), min = 2, max = 2, chooser = Players.You))),
    )

    // CR 202.3 (mana value of a spell)
    @Test
    fun cloudbeastScout() = card(
        name = "Cloudbeast Scout",
        cost = "{3}{G}",
        type = "Creature — Giant Archer",
        pt = "2/4",
        text = """
            Reach
            Whenever you cast a spell with mana value 5 or greater, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(youCast(and(spellFilter, manaValue(Comparison.AtLeast, 5))), createFood),
    )

    @Test
    fun webSnack() = card(
        name = "Web Snack",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Destroy up to one target artifact, enchantment, or creature with flying. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), createFood,
            targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying))), min = 0))),
    )

    @Test
    fun candytoothHag() = card(
        name = "Candytooth Hag",
        cost = "{2}{B}",
        type = "Creature — Human Warlock",
        pt = "3/2",
        text = """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {2}, Sacrifice a Food: Target player loses 2 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        activated(cost(mana("{2}"), sacrificeA(aFood)), loseLife(2, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 205.1a? no: CR 613.1d (type change), 611.2a; CR 702.? the Food ability sacrifices itself
    @Test
    fun hardBiscuit() = card(
        name = "Hard Biscuit",
        cost = "{1}{G}",
        type = "Artifact Creature — Food Golem",
        pt = "2/2",
        text = """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {2}{G}: Until end of turn, target noncreature artifact you control becomes a 4/4 artifact creature.
            {2}, {T}, Sacrifice this creature: You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        activated(cost(mana("{2}{G}")), untilEndOfTurn(Objects.Target("artifact"),
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(4), n(4))),
            targets = listOf(slot("artifact", and(not(creature), artifact, you)))),
        activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)),
    )

    // CR 714.2b; CR 107.1 (one plus the count)
    @Test
    fun greetingsToCandyville() = card(
        name = "Greetings to Candyville",
        cost = "{1}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create a 1/1 white Human creature token.
            II — Create a Food token.
            III — Put X +1/+1 counters on target creature you control, where X is one plus the number of Foods you control.
        """,
    ) parsesTo abilities(
        chapter(1, create(human)),
        chapter(2, createFood),
        chapter(3, counters(target("creature"), Value.Sum(listOf(n(1), foods))), targets = listOf(slot("creature", creatureYouControl))),
    )
}
