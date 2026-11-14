package arcanum.parser.sets.mh3

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.api.CostPart
import arcanum.api.CostObjects
import arcanum.api.Trigger
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 13: devoid (CR 702.114) cards with Eldrazi Spawn tokens, cast triggers (CR 603.1, 601.2i), a cost reduction
 * (CR 601.2f), evolve granted to tokens (CR 702.100) and colorless-spell triggers (CR 105.2c).
 */
class Mh3Chunk13DevoidTest {
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    /** "{T}: Add one mana of any color." */
    private val addAnyColor = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)))

    @Test
    fun ghostfireSlice() = card(
        name = "Hollowflame Lash",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Devoid (This card has no color.)
            This spell costs {2} less to cast if an opponent controls a multicolored permanent.
            Hollowflame Lash deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        devoid,
        static(Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(ManaCost.parse("{2}")!!, times = n(1))),
            condition = Condition.Exists(and(ObjectFilter.Multicolored, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Opponent)), 1)),
        spell(damage(4, toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun pathOfAnnihilation() = card(
        name = "Road of Unmaking",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            Devoid (This card has no color.)
            When this enchantment enters, create two 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
            Eldrazi you control have "{T}: Add one mana of any color."
            Whenever you cast a creature spell with mana value 7 or greater, you gain 4 life.
        """,
    ) parsesTo abilities(
        devoid,
        triggered(entersSelf, create(eldraziSpawn, n(2))),
        static(Mechanic.GainAbilities(listOf(addAnyColor)), affects = Objects.Each(and(sub("Eldrazi"), you))),
        triggered(youCast(and(creature, manaValue(Comparison.AtLeast, 7))), gainLife(4)),
    )

    @Test
    fun petrifyingMeddler() = card(
        name = "Gazing Meddler",
        cost = "{4}{U}",
        type = "Creature — Eldrazi",
        pt = "4/5",
        text = """
            Devoid (This card has no color.)
            When you cast this spell, tap up to one target creature and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Reach
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCastThis, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun),
            targets = listOf(slot("creature", creature, min = 0))),
        Keyword.Reach,
    )

    @Test
    fun propagatorDrone() = card(
        name = "Brood Tender Drone",
        cost = "{1}{G}",
        type = "Creature — Eldrazi Drone",
        pt = "2/2",
        text = """
            Devoid (This card has no color.)
            Creature tokens you control have evolve. (They have "Whenever a creature you control enters, if it has greater power or toughness than this token, put a +1/+1 counter on this token." They see this creature enter.)
            {3}{G}: Create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        """,
    ) parsesTo abilities(
        devoid,
        static(Mechanic.GainAbilities(listOf(Keyword.Evolve)), affects = Objects.Each(and(creature, token, you))),
        activated(manaCost("{3}{G}"), create(eldraziSpawn)),
    )

    @Test
    fun skitteringPrecursor() = card(
        name = "Scuttling Forerunner",
        cost = "{2}{R}",
        type = "Creature — Eldrazi Drone",
        pt = "3/3",
        text = """
            Devoid (This card has no color.)
            Menace
            Whenever you sacrifice a nontoken permanent, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Menace,
        triggered(on(EventPattern.Sacrifice(and(not(token), permanentFilter, you))), create(eldraziSpawn)),
    )

    @Test
    fun snappingVoidcraw() = card(
        name = "Biting Voidshell",
        cost = "{1}{G}{U}",
        type = "Creature — Eldrazi Turtle",
        pt = "1/3",
        text = """
            Devoid (This card has no color.)
            {T}: Add {C}{C}.
            {3}{C}, {T}: Draw a card.
        """,
    ) parsesTo abilities(
        devoid,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You))),
        activated(cost(mana("{3}{C}"), tap), draw(1)),
    )

    @Test
    fun sowingMycospawn() = card(
        name = "Seeding Sporemass",
        cost = "{3}{G}",
        type = "Creature — Eldrazi Fungus",
        pt = "3/3",
        text = """
            Devoid (This card has no color.)
            Kicker {1}{C} (You may pay an additional {1}{C} as you cast this spell.)
            When you cast this spell, search your library for a land card, put it onto the battlefield, then shuffle.
            When you cast this spell, if it was kicked, exile target land.
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Kicker(listOf(manaCost("{1}{C}"))),
        triggered(youCastThis, search(land), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
        triggered(youCastThis, move(target("land"), Zone.Exile), targets = listOf(slot("land", land)), interveningIf = kicked),
    )

    @Test
    fun spawnGangCommander() = card(
        name = "Brood-Pack Warlord",
        cost = "{3}{R}{R}",
        type = "Creature — Eldrazi Goblin",
        pt = "2/2",
        text = """
            Devoid (This card has no color.)
            When you cast this spell, create three 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
            {1}{C}, Sacrifice an Eldrazi: This creature deals 2 damage to any target.
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCastThis, create(eldraziSpawn, n(3))),
        activated(cost(mana("{1}{C}"), sacrificeA(sub("Eldrazi"))), damage(2, toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun territoryCuller() = card(
        name = "Frontier Reaper",
        cost = "{4}{G}",
        type = "Creature — Eldrazi",
        pt = "7/5",
        text = """
            Devoid (This card has no color.)
            Reach
            Landfall — Whenever a land you control enters, look at the top card of your library. If it's a creature card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.
        """,
    ) parsesTo abilities(
        devoid,
        Keyword.Reach,
        triggered(enters(and(land, you)),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "top")),
            ifThen(Condition.ObjectMatches(Objects.Bound("top"), creature),
                Step.Pay(null, steps = listOf(Step.Do(Effect.Reveal(Objects.Bound("top"))), move(Objects.Bound("top"), Zone.Hand)),
                    payer = Players.You, mandatory = false, bind = "kept")),
            ifThen(Condition.Not(Condition.Did("kept")), Step.May(listOf(move(Objects.Bound("top"), Zone.Graveyard)), Players.You)),
            abilityWord = "Landfall"),
    )

    @Test
    fun titansVanguard() = card(
        name = "Giants' Spearhead",
        cost = "{3}{R}{G}",
        type = "Creature — Eldrazi",
        pt = "5/5",
        text = """
            Devoid (This card has no color.)
            When you cast this spell and whenever this creature attacks, put a +1/+1 counter on each colorless creature you control.
            Trample
        """,
    ) parsesTo abilities(
        devoid,
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Is(Objects.Self), PlayerFilter.You), EventPattern.Attack(ObjectFilter.Is(Objects.Self)))),
            counters(Objects.Each(and(ObjectFilter.Colorless, creature, you)))),
        Keyword.Trample,
    )

    @Test
    fun uginSBinding() = card(
        name = "Elder's Fetter",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Devoid (This card has no color.)
            Return target nonland permanent you don't control to its owner's hand.
            Whenever you cast a colorless spell with mana value 7 or greater, you may exile this card from your graveyard. When you do, return each nonland permanent you don't control to its owner's hand.
        """,
    ) parsesTo abilities(
        devoid,
        spell(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
        triggered(youCast(and(ObjectFilter.Colorless, spellFilter, manaValue(Comparison.AtLeast, 7))),
            Step.Pay(cost(CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(move(Objects.Each(and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))), Zone.Hand))),
            functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun unfathomableTruths() = card(
        name = "Fathomless Lessons",
        cost = "{4}{U}",
        type = "Instant",
        text = """
            Devoid (This card has no color.)
            Draw three cards and create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        """,
    ) parsesTo abilities(
        devoid,
        spell(draw(3), create(eldraziSpawn)),
    )

    @Test
    fun writhingChrysalis() = card(
        name = "Twisting Cocoon",
        cost = "{2}{R}{G}",
        type = "Creature — Eldrazi Drone",
        pt = "2/3",
        text = """
            Devoid (This card has no color.)
            When you cast this spell, create two 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
            Reach
            Whenever you sacrifice another Eldrazi, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        devoid,
        triggered(youCastThis, create(eldraziSpawn, n(2))),
        Keyword.Reach,
        triggered(on(EventPattern.Sacrifice(and(another, sub("Eldrazi"), you))), counters(Objects.Self)),
    )

}